package com.service_demandes.service;

import com.service_demandes.dto.CreateAppelDonRequest;
import com.service_demandes.entity.AppelDon;
import com.service_demandes.entity.Composant;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.StatutAppelDon;
import com.service_demandes.exception.RessourceIntrouvableException;
import com.service_demandes.repository.AppelDonRepository;
import com.service_demandes.repository.DemandeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gestion des appels publics aux dons.
 *
 * <ul>
 *   <li>Ouverture automatique sur pénurie : {@link DemandeService} appelle
 *       {@link #ouvrirOuReutiliser} dans sa transaction.</li>
 *   <li>Réutilisation d'un appel déjà OUVERT pour le même groupe + composant (R16) :
 *       évite de multiplier les appels identiques.</li>
 *   <li>Ouverture et fermeture manuelles par le staff hospitalier.</li>
 *   <li>Fermeture automatique par {@link ReconciliationJob} quand le stock remonte.</li>
 *   <li>Lecture publique sans authentification ({@code GET /api/v1/appels}).</li>
 * </ul>
 */
@Service
public class AppelDonService {

    private static final Logger log = LoggerFactory.getLogger(AppelDonService.class);

    private final AppelDonRepository appelDonRepository;
    private final DemandeRepository demandeRepository;

    public AppelDonService(AppelDonRepository appelDonRepository, DemandeRepository demandeRepository) {
        this.appelDonRepository = appelDonRepository;
        this.demandeRepository  = demandeRepository;
    }

    // -------------------------------------------------------------------------
    // Ouverture automatique sur EN_PENURIE
    // -------------------------------------------------------------------------

    /**
     * Ouvre un appel pour le groupe + composant de la demande en pénurie,
     * ou réutilise un appel OUVERT existant pour cette combinaison (R16).
     * Doit être appelé à l'intérieur d'une transaction active.
     */
    @Transactional
    public AppelDon ouvrirOuReutiliser(Demande demandeEnPenurie) {
        return appelDonRepository
                .findFirstByGroupeSanguinAndComposantAndStatut(
                        demandeEnPenurie.getGroupeSanguin(),
                        demandeEnPenurie.getComposant(),
                        StatutAppelDon.OUVERT)
                .orElseGet(() -> creerNouvelAppel(demandeEnPenurie));
    }

    private AppelDon creerNouvelAppel(Demande demandeEnPenurie) {
        AppelDon nouvelAppel = AppelDon.builder()
                .demande(demandeEnPenurie)
                .groupeSanguin(demandeEnPenurie.getGroupeSanguin())
                .composant(demandeEnPenurie.getComposant())
                .statut(StatutAppelDon.OUVERT)
                .build();
        log.info("Appel public créé automatiquement : {} / {}",
                demandeEnPenurie.getGroupeSanguin(), demandeEnPenurie.getComposant());
        return appelDonRepository.save(nouvelAppel);
    }

    // -------------------------------------------------------------------------
    // Ouverture manuelle (POST /api/v1/appels)
    // -------------------------------------------------------------------------

    @Transactional
    public AppelDon ouvrir(CreateAppelDonRequest corps) {
        Demande demandeAssociee = chargerDemandeAssocieeSiPresente(corps);

        AppelDon nouvelAppel = AppelDon.builder()
                .demande(demandeAssociee)
                .groupeSanguin(corps.groupeSanguin())
                .composant(corps.composant())
                .centreCibleId(corps.centreCibleId())
                .statut(StatutAppelDon.OUVERT)
                .build();

        AppelDon appelSauvegarde = appelDonRepository.save(nouvelAppel);
        log.info("Appel public créé manuellement : id={} {} / {}",
                appelSauvegarde.getId(), corps.groupeSanguin(), corps.composant());
        return appelSauvegarde;
    }

    private Demande chargerDemandeAssocieeSiPresente(CreateAppelDonRequest corps) {
        if (corps.demandeId() == null) {
            return null;
        }
        return demandeRepository.findById(corps.demandeId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Demande associée introuvable : " + corps.demandeId()));
    }

    // -------------------------------------------------------------------------
    // Fermeture manuelle (PATCH /api/v1/appels/{id})
    // -------------------------------------------------------------------------

    /**
     * Ferme un appel. Idempotent : retourne l'appel tel quel s'il est déjà fermé.
     */
    @Transactional
    public AppelDon fermer(UUID appelId) {
        AppelDon appel = appelDonRepository.findById(appelId)
                .orElseThrow(() -> new RessourceIntrouvableException("Appel introuvable : " + appelId));

        if (appel.getStatut() == StatutAppelDon.FERME) {
            return appel; // déjà fermé, opération idempotente
        }

        appel.setStatut(StatutAppelDon.FERME);
        appel.setDateFermeture(Instant.now());
        log.info("Appel {} fermé manuellement", appelId);
        return appelDonRepository.save(appel);
    }

    // -------------------------------------------------------------------------
    // Fermeture automatique par ReconciliationJob
    // -------------------------------------------------------------------------

    /**
     * Ferme tous les appels OUVERT pour un groupe + composant donné.
     * Appelé quand le stock remonte au-dessus du seuil.
     *
     * @return le nombre d'appels fermés
     */
    @Transactional
    public int fermerTousLesAppelsOuvertsPour(GroupeSanguin groupeSanguin, Composant composant) {
        List<AppelDon> appelsAFermer = trouverAppelsOuvertsPour(groupeSanguin, composant);

        if (appelsAFermer.isEmpty()) {
            return 0;
        }

        Instant maintenant = Instant.now();
        appelsAFermer.forEach(appel -> {
            appel.setStatut(StatutAppelDon.FERME);
            appel.setDateFermeture(maintenant);
        });
        appelDonRepository.saveAll(appelsAFermer);

        log.info("Réconciliation : {} appel(s) fermé(s) automatiquement pour {} / {}",
                appelsAFermer.size(), groupeSanguin, composant);
        return appelsAFermer.size();
    }

    private List<AppelDon> trouverAppelsOuvertsPour(GroupeSanguin groupeSanguin, Composant composant) {
        return appelDonRepository.findByStatut(StatutAppelDon.OUVERT).stream()
                .filter(appel -> appel.getGroupeSanguin() == groupeSanguin
                              && appel.getComposant() == composant)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Lecture publique (GET /api/v1/appels)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AppelDon> lister(StatutAppelDon filtreStatut) {
        if (filtreStatut != null) {
            return appelDonRepository.findByStatutOrderByDateOuvertureDesc(filtreStatut);
        }
        return appelDonRepository.findAll();
    }

    @Transactional(readOnly = true)
    public AppelDon lire(UUID appelId) {
        return appelDonRepository.findById(appelId)
                .orElseThrow(() -> new RessourceIntrouvableException("Appel introuvable : " + appelId));
    }
}
