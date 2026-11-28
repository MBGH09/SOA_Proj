package com.service_demandes.service;

import com.service_demandes.dto.CreateAppelDonRequest;
import com.service_demandes.entity.AppelDon;
import com.service_demandes.entity.Demande;
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
 * S6-07 — Gestion des appels publics aux dons.
 *
 * <ul>
 *   <li>Ouverture automatique sur pénurie (appelé par {@link DemandeService}).</li>
 *   <li>Réutilisation d'un appel déjà OUVERT pour le même groupe + composant.</li>
 *   <li>Ouverture manuelle par le staff hospitalier.</li>
 *   <li>Fermeture manuelle ou automatique (par le job de réconciliation).</li>
 *   <li>Lecture publique sans authentification.</li>
 * </ul>
 */
@Service
public class AppelDonService {

    private static final Logger log = LoggerFactory.getLogger(AppelDonService.class);

    private final AppelDonRepository appels;
    private final DemandeRepository demandes;

    public AppelDonService(AppelDonRepository appels, DemandeRepository demandes) {
        this.appels = appels;
        this.demandes = demandes;
    }

    // -------------------------------------------------------------------------
    // Ouverture automatique sur EN_PENURIE (appelé par DemandeService, dans sa transaction)
    // -------------------------------------------------------------------------

    /**
     * Ouvre un appel pour la demande en pénurie, ou réutilise un appel OUVERT existant
     * pour le même groupe + composant (R16).
     * Cette méthode doit être appelée <em>à l'intérieur d'une transaction active</em>.
     */
    @Transactional
    public AppelDon ouvrirOuReutiliser(Demande demande) {
        return appels
                .findFirstByGroupeSanguinAndComposantAndStatut(
                        demande.getGroupeSanguin(), demande.getComposant(), StatutAppelDon.OUVERT)
                .orElseGet(() -> {
                    AppelDon nouvel = AppelDon.builder()
                            .demande(demande)
                            .groupeSanguin(demande.getGroupeSanguin())
                            .composant(demande.getComposant())
                            .statut(StatutAppelDon.OUVERT)
                            .build();
                    log.info("Nouvel appel public ouvert : {} / {}", demande.getGroupeSanguin(), demande.getComposant());
                    return appels.save(nouvel);
                });
    }

    // -------------------------------------------------------------------------
    // Ouverture manuelle (POST /api/v1/appels)
    // -------------------------------------------------------------------------

    @Transactional
    public AppelDon ouvrir(CreateAppelDonRequest requete) {
        // Résoudre la demande associée si un identifiant est fourni
        Demande demande = null;
        if (requete.demandeId() != null) {
            demande = demandes.findById(requete.demandeId())
                    .orElseThrow(() -> new RessourceIntrouvableException(
                            "Demande introuvable : " + requete.demandeId()));
        }

        AppelDon appel = AppelDon.builder()
                .demande(demande)
                .groupeSanguin(requete.groupeSanguin())
                .composant(requete.composant())
                .centreCibleId(requete.centreCibleId())
                .statut(StatutAppelDon.OUVERT)
                .build();

        AppelDon sauvegarde = appels.save(appel);
        log.info("Appel public ouvert manuellement : id={} {} / {}", sauvegarde.getId(),
                requete.groupeSanguin(), requete.composant());
        return sauvegarde;
    }

    // -------------------------------------------------------------------------
    // Fermeture manuelle (PATCH /api/v1/appels/{id})
    // -------------------------------------------------------------------------

    @Transactional
    public AppelDon fermer(UUID id) {
        AppelDon appel = appels.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Appel introuvable : " + id));

        if (appel.getStatut() == StatutAppelDon.FERME) {
            // Idempotent : déjà fermé, on retourne tel quel
            return appel;
        }

        appel.setStatut(StatutAppelDon.FERME);
        appel.setDateFermeture(Instant.now());
        log.info("Appel {} fermé manuellement", id);
        return appels.save(appel);
    }

    // -------------------------------------------------------------------------
    // Fermeture automatique par le job de réconciliation (S6-10)
    // -------------------------------------------------------------------------

    /**
     * Ferme automatiquement les appels OUVERT pour un groupe + composant donné.
     * Appelé par le {@link ReconciliationJob} quand le stock remonte au-dessus du seuil.
     */
    @Transactional
    public int fermerAutomatiquement(
            com.service_demandes.entity.GroupeSanguin groupe,
            com.service_demandes.entity.Composant composant) {

        List<AppelDon> ouverts = appels
                .findFirstByGroupeSanguinAndComposantAndStatut(groupe, composant, StatutAppelDon.OUVERT)
                .stream().toList();

        // Ferme tous les appels ouverts pour cette combinaison
        List<AppelDon> tousOuverts = appels.findByStatut(StatutAppelDon.OUVERT).stream()
                .filter(a -> a.getGroupeSanguin() == groupe && a.getComposant() == composant)
                .toList();

        Instant maintenant = Instant.now();
        tousOuverts.forEach(a -> {
            a.setStatut(StatutAppelDon.FERME);
            a.setDateFermeture(maintenant);
        });
        appels.saveAll(tousOuverts);

        if (!tousOuverts.isEmpty()) {
            log.info("Job réconciliation : {} appel(s) fermé(s) pour {} / {}", tousOuverts.size(), groupe, composant);
        }
        return tousOuverts.size();
    }

    // -------------------------------------------------------------------------
    // Lecture (GET /api/v1/appels)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AppelDon> lister(StatutAppelDon statut) {
        if (statut != null) {
            return appels.findByStatutOrderByDateOuvertureDesc(statut);
        }
        return appels.findAll();
    }

    @Transactional(readOnly = true)
    public AppelDon lire(UUID id) {
        return appels.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Appel introuvable : " + id));
    }
}
