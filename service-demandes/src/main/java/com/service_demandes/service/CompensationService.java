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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Enregistrement des compensations familiales sur une demande de sang.
 *
 * <p>Règles appliquées :</p>
 * <ul>
 *   <li>R17 : la demande doit avoir {@code compensationDemandee} ≥ 1.</li>
 *   <li>R26 : un numéro de certificat ne compte qu'une seule fois (contrainte UNIQUE en base).</li>
 *   <li>Appel 4 (optionnel) : vérifie la validité du numéro avec service-donneurs avant
 *       de l'enregistrer. Si service-donneurs est down, {@link ServiceIndisponibleException}
 *       est levée (503) → le staff corrige à la main via {@code PATCH /demandes/{id}}.</li>
 * </ul>
 */
@Service
public class CompensationService {

    private static final Logger log = LoggerFactory.getLogger(CompensationService.class);

    private final CompensationRepository compensationRepository;
    private final DemandeRepository demandeRepository;
    private final DonneursClient donneursClient;

    public CompensationService(CompensationRepository compensationRepository,
                               DemandeRepository demandeRepository,
                               DonneursClient donneursClient) {
        this.compensationRepository = compensationRepository;
        this.demandeRepository      = demandeRepository;
        this.donneursClient         = donneursClient;
    }

    // -------------------------------------------------------------------------
    // Enregistrement (POST /api/v1/demandes/{id}/compensations)
    // -------------------------------------------------------------------------

    /**
     * Enregistre un donneur de compensation identifié par son numéro de certificat.
     *
     * @param demandeId     identifiant de la demande hospitalière
     * @param corps         corps de la requête contenant {@code numeroCertificat}
     * @return              la compensation créée
     * @throws RessourceIntrouvableException si la demande est inconnue (404)
     * @throws RequeteInvalideException      si la demande n'attend pas de compensation (400)
     *                                        ou si le certificat est invalide (400)
     * @throws ConflitMetierException        si le numéro de certificat est déjà utilisé (409, R26)
     * @throws ServiceIndisponibleException  si service-donneurs est injoignable (503)
     */
    @Transactional
    public Compensation enregistrer(UUID demandeId, CreateCompensationRequest corps) {
        Demande demande = chargerDemandeOuEchouer(demandeId);
        verifierQueDemandeAccepteDesCompensations(demande);

        String numeroCertificat = corps.numeroCertificat().trim();
        verifierUniciteNumeroCertificat(numeroCertificat);
        verifierValiditeCertificatDansServiceDonneurs(numeroCertificat);

        Compensation compensation = sauvegarderCompensation(demande, numeroCertificat);
        rafraichirCompteurCompensationsReçues(demande, demandeId);

        log.info("Compensation enregistrée : demande={} certificat={} total={}/{}",
                demandeId, numeroCertificat,
                demande.getCompensationRecue(), demande.getCompensationDemandee());
        return compensation;
    }

    private Demande chargerDemandeOuEchouer(UUID demandeId) {
        return demandeRepository.findById(demandeId)
                .orElseThrow(() -> new RessourceIntrouvableException("Demande introuvable : " + demandeId));
    }

    private void verifierQueDemandeAccepteDesCompensations(Demande demande) {
        boolean aucuneCompensationDemandee =
                demande.getCompensationDemandee() == null || demande.getCompensationDemandee() < 1;
        if (aucuneCompensationDemandee) {
            throw new RequeteInvalideException(
                    "Cette demande n'a pas de compensation familiale demandée");
        }
    }

    private void verifierUniciteNumeroCertificat(String numeroCertificat) {
        if (compensationRepository.findByNumeroCertificat(numeroCertificat).isPresent()) {
            throw new ConflitMetierException(
                    "Le numéro de certificat " + numeroCertificat + " a déjà été enregistré (R26)");
        }
    }

    private Compensation sauvegarderCompensation(Demande demande, String numeroCertificat) {
        Compensation compensation = Compensation.builder()
                .demande(demande)
                .numeroCertificat(numeroCertificat)
                .build();
        try {
            return compensationRepository.save(compensation);
        } catch (DataIntegrityViolationException erreurDoublonConcurrent) {
            // Deux requêtes simultanées ont passé la vérification d'unicité :
            // la contrainte UNIQUE de la base de données attrape la seconde.
            throw new ConflitMetierException(
                    "Le numéro de certificat " + numeroCertificat
                    + " a déjà été enregistré par un appel concurrent (R26)");
        }
    }

    private void rafraichirCompteurCompensationsReçues(Demande demande, UUID demandeId) {
        long nombreDeCompensationsEnregistrees = compensationRepository.countByDemandeId(demandeId);
        demande.setCompensationRecue((int) nombreDeCompensationsEnregistrees);
        demandeRepository.save(demande);
    }

    // -------------------------------------------------------------------------
    // Vérification du certificat dans service-donneurs (appel 4, optionnel)
    // -------------------------------------------------------------------------

    /**
     * Vérifie que le numéro de certificat est valide en appelant
     * {@code GET /api/v1/certificats/{numero}} sur service-donneurs.
     *
     * <ul>
     *   <li>404 de Donneurs → certificat inconnu → {@link RequeteInvalideException} (400)</li>
     *   <li>Donneurs down ou disjoncteur ouvert → {@link ServiceIndisponibleException} (503)
     *       → le staff utilise {@code PATCH /demandes/{id}} pour corriger manuellement</li>
     * </ul>
     */
    private void verifierValiditeCertificatDansServiceDonneurs(String numeroCertificat) {
        try {
            donneursClient.verifierCertificat(numeroCertificat);
        } catch (CertificatInvalideException erreurValidation) {
            throw new RequeteInvalideException(
                    "Certificat inconnu ou invalide : " + numeroCertificat);
        } catch (DonneursIndisponibleException erreurReseau) {
            log.warn("service-donneurs injoignable pour vérifier le certificat {} : {}",
                    numeroCertificat, erreurReseau.getMessage());
            throw new ServiceIndisponibleException(
                    "Impossible de vérifier le certificat : service-donneurs injoignable. "
                    + "Utilisez PATCH /demandes/{id} pour corriger compensationRecue à la main.");
        }
    }

    // -------------------------------------------------------------------------
    // Lecture (GET /api/v1/demandes/{id}/compensations)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Compensation> lister(UUID demandeId) {
        chargerDemandeOuEchouer(demandeId); // vérifie l'existence de la demande
        return compensationRepository.findByDemandeId(demandeId);
    }

    @Transactional(readOnly = true)
    public long compterCompensations(UUID demandeId) {
        return compensationRepository.countByDemandeId(demandeId);
    }
}
