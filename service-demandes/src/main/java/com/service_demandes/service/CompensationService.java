package com.service_demandes.service;

import com.service_demandes.client.DonneursClient;
import com.service_demandes.client.DonneursClient.CertificatInvalideException;
import com.service_demandes.client.DonneursClient.DonneursIndisponibleException;
import com.service_demandes.dto.CreateCompensationRequest;
import com.service_demandes.entity.Compensation;
import com.service_demandes.entity.Demande;
import com.service_demandes.exception.ConflitMetierException;
import com.service_demandes.exception.RequeteInvalideException;
import com.service_demandes.exception.RessourceIntrouvableException;
import com.service_demandes.exception.ServiceIndisponibleException;
import com.service_demandes.repository.CompensationRepository;
import com.service_demandes.repository.DemandeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * S6-08 — Enregistrement des compensations familiales.
 *
 * <p>Règles appliquées :</p>
 * <ul>
 *   <li>R17 : la demande doit avoir {@code compensationDemandee} ≥ 2.</li>
 *   <li>R26 : un numéro de certificat ne compte qu'une seule fois (contrainte UNIQUE en base).</li>
 *   <li>Appel 4 (optionnel) : vérifie le numéro avec {@code GET /api/v1/certificats/{numero}}
 *       dans service-donneurs, protégé par un disjoncteur. Si Donneurs est down,
 *       {@link ServiceIndisponibleException} est levée → le staff corrige à la main via PATCH.</li>
 * </ul>
 */
@Service
public class CompensationService {

    private static final Logger log = LoggerFactory.getLogger(CompensationService.class);

    private final CompensationRepository compensations;
    private final DemandeRepository demandes;
    private final DonneursClient donneursClient;

    public CompensationService(CompensationRepository compensations,
                               DemandeRepository demandes,
                               DonneursClient donneursClient) {
        this.compensations = compensations;
        this.demandes = demandes;
        this.donneursClient = donneursClient;
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/demandes/{id}/compensations
    // -------------------------------------------------------------------------

    /**
     * Enregistre un donneur de compensation identifié par son numéro de certificat.
     *
     * @param demandeId        identifiant de la demande hospitalière
     * @param requete          corps contenant {@code numeroCertificat}
     * @return la compensation créée
     * @throws RessourceIntrouvableException si la demande est inconnue (404)
     * @throws RequeteInvalideException      si la demande ne demande pas de compensation (400)
     * @throws ConflitMetierException        si le numéro est déjà utilisé (409, R26)
     * @throws ServiceIndisponibleException  si service-donneurs est injoignable (503)
     */
    @Transactional
    public Compensation enregistrer(UUID demandeId, CreateCompensationRequest requete) {

        // --- Charger la demande ---
        Demande demande = demandes.findById(demandeId)
                .orElseThrow(() -> new RessourceIntrouvableException("Demande introuvable : " + demandeId));

        // --- R17 : la demande doit avoir demandé une compensation ---
        if (demande.getCompensationDemandee() == null || demande.getCompensationDemandee() < 1) {
            throw new RequeteInvalideException(
                    "Cette demande n'a pas de compensation familiale demandée");
        }

        String numero = requete.numeroCertificat().trim();

        // --- R26 : vérifier l'unicité avant l'appel réseau pour détecter vite un doublon ---
        if (compensations.findByNumeroCertificat(numero).isPresent()) {
            throw new ConflitMetierException(
                    "Le numéro de certificat " + numero + " a déjà été enregistré (R26)");
        }

        // --- Appel 4 optionnel : vérifier la validité du certificat dans service-donneurs ---
        verifierCertificatDonneurs(numero);

        // --- Sauvegarder la compensation ---
        Compensation compensation = Compensation.builder()
                .demande(demande)
                .numeroCertificat(numero)
                .build();

        try {
            compensation = compensations.save(compensation);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Double insertion concurrente : la contrainte UNIQUE en base l'attrape
            throw new ConflitMetierException(
                    "Le numéro de certificat " + numero + " a déjà été enregistré (contrainte R26)");
        }

        // --- Incrémenter le compteur sur la demande ---
        long total = compensations.countByDemandeId(demandeId);
        demande.setCompensationRecue((int) total);
        demandes.save(demande);

        log.info("Compensation enregistrée : demande={} certificat={} total={}/{}",
                demandeId, numero, demande.getCompensationRecue(), demande.getCompensationDemandee());

        return compensation;
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/demandes/{id}/compensations
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Compensation> lister(UUID demandeId) {
        demandes.findById(demandeId)
                .orElseThrow(() -> new RessourceIntrouvableException("Demande introuvable : " + demandeId));
        return compensations.findByDemandeId(demandeId);
    }

    /** Nombre de compensations valides pour une demande (utilisé par le controller). */
    @Transactional(readOnly = true)
    public long compterCompensations(UUID demandeId) {
        return compensations.countByDemandeId(demandeId);
    }

    // -------------------------------------------------------------------------
    // Vérification du certificat dans service-donneurs (appel 4, optionnel)
    // -------------------------------------------------------------------------

    /**
     * Appelle {@code GET /api/v1/certificats/{numero}} sur service-donneurs.
     *
     * <ul>
     *   <li>404 → le certificat est invalide ou inconnu → {@link RequeteInvalideException} (400)</li>
     *   <li>Donneurs down / disjoncteur ouvert → {@link ServiceIndisponibleException} (503)
     *       → le staff utilise {@code PATCH /demandes/{id}} pour corriger manuellement</li>
     * </ul>
     */
    private void verifierCertificatDonneurs(String numero) {
        try {
            boolean valide = donneursClient.verifierCertificat(numero);
            if (!valide) {
                throw new RequeteInvalideException("Certificat invalide : " + numero);
            }
        } catch (CertificatInvalideException e) {
            throw new RequeteInvalideException("Certificat inconnu ou invalide : " + numero);
        } catch (DonneursIndisponibleException e) {
            log.warn("service-donneurs indisponible pour la vérification du certificat {} : {}", numero, e.getMessage());
            throw new ServiceIndisponibleException(
                    "Impossible de vérifier le certificat : service-donneurs injoignable. "
                    + "Utilisez PATCH /demandes/{id} pour corriger compensationRecue à la main.");
        }
    }
}
