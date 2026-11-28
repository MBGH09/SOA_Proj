package com.service_demandes.service;

import com.service_demandes.client.DonStockClient;
import com.service_demandes.client.DonStockIndisponibleException;
import com.service_demandes.client.ReservationCommande;
import com.service_demandes.client.ReservationResultat;
import com.service_demandes.client.StatutReservation;
import com.service_demandes.config.DemandesRulesProperties;
import com.service_demandes.dto.CreateDemandeRequest;
import com.service_demandes.dto.ModifierDemandeRequest;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.exception.ConflitMetierException;
import com.service_demandes.exception.RequeteInvalideException;
import com.service_demandes.exception.RessourceIntrouvableException;
import com.service_demandes.repository.DemandeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestion du cycle de vie des demandes de sang.
 *
 * <p>Pattern deux transactions pour chaque création :</p>
 * <ol>
 *   <li>Transaction 1 : persiste la demande en {@code EN_ATTENTE} avant tout appel réseau.</li>
 *   <li>Appel gRPC {@code VerifierEtReserver} hors transaction (pas de verrou DB pendant le réseau).</li>
 *   <li>Transaction 2 : met à jour le statut selon la réponse gRPC.</li>
 * </ol>
 *
 * <p>Si Don & Stock est injoignable, la demande reste {@code EN_ATTENTE} et
 * {@link ReconciliationJob} la reprend à intervalle régulier.</p>
 */
@Service
public class DemandeService {

    private static final Logger log = LoggerFactory.getLogger(DemandeService.class);

    private final DemandeRepository demandeRepository;
    private final DonStockClient donStockClient;
    private final AppelDonService appelDonService;
    private final TransactionTemplate transactionTemplate;
    private final DemandesRulesProperties regles;

    /**
     * Identifiants des demandes dont l'appel gRPC est actuellement en vol.
     * Bloque toute annulation concurrente jusqu'à la fin de l'appel.
     */
    private final Set<UUID> reservationsEnCours = ConcurrentHashMap.newKeySet();

    public DemandeService(DemandeRepository demandeRepository,
                          DonStockClient donStockClient,
                          AppelDonService appelDonService,
                          TransactionTemplate transactionTemplate,
                          DemandesRulesProperties regles) {
        this.demandeRepository = demandeRepository;
        this.donStockClient     = donStockClient;
        this.appelDonService    = appelDonService;
        this.transactionTemplate = transactionTemplate;
        this.regles              = regles;
    }

    // -------------------------------------------------------------------------
    // Création (POST /api/v1/demandes)
    // -------------------------------------------------------------------------

    /**
     * Crée une demande et tente immédiatement la réservation gRPC.
     * Retourne 201 si le sang est disponible localement, 202 dans tous les autres cas.
     */
    public Demande creer(CreateDemandeRequest corps) {
        validerIntervalleCompensation(corps.compensationDemandee());

        Demande demandeEnAttente = persisterEnAttente(corps);

        ReservationResultat resultatReservation = tenterReservationGrpc(demandeEnAttente);
        if (resultatReservation == null) {
            return demandeEnAttente; // Don & Stock injoignable, le job reprend plus tard
        }

        return transactionTemplate.execute(
                ignoree -> appliquerResultatReservation(demandeEnAttente.getId(), resultatReservation));
    }

    private Demande persisterEnAttente(CreateDemandeRequest corps) {
        return transactionTemplate.execute(ignoree -> demandeRepository.save(
                Demande.builder()
                        .hopitalId(corps.hopitalId())
                        .hopitalNom(corps.hopitalNom())
                        .patientRef(corps.patientRef())
                        .motif(corps.motif())
                        .groupeSanguin(corps.groupeSanguin())
                        .composant(corps.composant())
                        .quantitePoches(corps.quantitePoches())
                        .urgence(corps.urgence())
                        .statut(StatutDemande.EN_ATTENTE)
                        .compensationDemandee(corps.compensationDemandee())
                        .build()));
    }

    /**
     * Appelle Don & Stock hors transaction. Retourne null si le service est injoignable.
     */
    private ReservationResultat tenterReservationGrpc(Demande demandeEnAttente) {
        reservationsEnCours.add(demandeEnAttente.getId());
        try {
            return donStockClient.verifierEtReserver(construireCommandeReservation(demandeEnAttente));
        } catch (DonStockIndisponibleException erreurReseau) {
            log.warn("Don & Stock injoignable pour la demande {} — laissée EN_ATTENTE : {}",
                    demandeEnAttente.getId(), erreurReseau.getMessage());
            return null;
        } finally {
            reservationsEnCours.remove(demandeEnAttente.getId());
        }
    }

    private ReservationCommande construireCommandeReservation(Demande demande) {
        return new ReservationCommande(
                demande.getId(),
                demande.getHopitalId(),
                demande.getGroupeSanguin(),
                demande.getComposant(),
                demande.getQuantitePoches(),
                demande.getUrgence());
    }

    // -------------------------------------------------------------------------
    // Application du résultat gRPC (appelé aussi par ReconciliationJob)
    // -------------------------------------------------------------------------

    /**
     * Applique le résultat d'un {@code VerifierEtReserver} sur la demande persistée.
     * Doit être appelé à l'intérieur d'une transaction active.
     */
    Demande appliquerResultatReservation(UUID demandeId, ReservationResultat resultat) {
        Demande demande = demandeRepository.findByIdVerrouillee(demandeId)
                .orElseThrow(() -> new IllegalStateException(
                        "Demande introuvable pendant l'application du résultat : " + demandeId));

        if (demande.getStatut() == StatutDemande.ANNULEE) {
            libererReservationSiNecessaire(demandeId, resultat);
            log.info("Demande {} annulée pendant la réservation — résultat ignoré", demandeId);
            return demande;
        }

        if (demande.getStatut() != StatutDemande.EN_ATTENTE) {
            log.info("Demande {} déjà au statut {} — résultat gRPC ignoré", demandeId, demande.getStatut());
            return demande;
        }

        mettreAJourStatutDepuisResultat(demande, resultat);
        return demandeRepository.save(demande);
    }

    private void libererReservationSiNecessaire(UUID demandeId, ReservationResultat resultat) {
        if (resultat.statut() != StatutReservation.RESERVEE_STOCK_LOCAL) {
            return;
        }
        try {
            donStockClient.libererReservation(demandeId, resultat.reference());
        } catch (RuntimeException erreurLiberation) {
            log.warn("Libération impossible pour la réservation {} de la demande {} : {}",
                    resultat.reference(), demandeId, erreurLiberation.getMessage());
        }
    }

    /**
     * Traduit le statut gRPC en statut métier (table de mapping étude section 7.3).
     */
    private void mettreAJourStatutDepuisResultat(Demande demande, ReservationResultat resultat) {
        switch (resultat.statut()) {
            case RESERVEE_STOCK_LOCAL -> {
                demande.setStatut(StatutDemande.SATISFAITE);
                demande.setReferenceApprovisionnement(resultat.reference());
            }
            case APPROVISIONNEMENT_COMMANDE, EN_ATTENTE_CONFIRMATION -> {
                demande.setStatut(StatutDemande.APPROVISIONNEMENT_EN_COURS);
                demande.setReferenceApprovisionnement(resultat.reference());
            }
            case STOCK_INSUFFISANT_PARTOUT -> {
                demande.setStatut(StatutDemande.EN_PENURIE);
                appelDonService.ouvrirOuReutiliser(demande);
                log.info("Pénurie confirmée — appel public ouvert pour {} / {}",
                        demande.getGroupeSanguin(), demande.getComposant());
            }
            case LIVREE -> {
                // Peut arriver quand le job de réconciliation retrouve une livraison terminée
                demande.setStatut(StatutDemande.SATISFAITE);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lecture (GET /api/v1/demandes et GET /api/v1/demandes/{id})
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Demande> lister(StatutDemande filtreStatut, String filtreHopitalId) {
        Specification<Demande> specification = construireSpecification(filtreStatut, filtreHopitalId);
        Sort triParDateDecroissante = Sort.sort(Demande.class).by(Demande::getDateCreation).descending();
        return demandeRepository.findAll(specification, triParDateDecroissante);
    }

    private Specification<Demande> construireSpecification(StatutDemande filtreStatut, String filtreHopitalId) {
        Specification<Demande> specification = Specification.where(null);
        if (filtreStatut != null) {
            specification = specification.and(
                    (racine, query, builder) -> builder.equal(racine.get("statut"), filtreStatut));
        }
        if (filtreHopitalId != null && !filtreHopitalId.isBlank()) {
            specification = specification.and(
                    (racine, query, builder) -> builder.equal(racine.get("hopitalId"), filtreHopitalId));
        }
        return specification;
    }

    @Transactional(readOnly = true)
    public Demande lire(UUID demandeId) {
        return demandeRepository.findById(demandeId)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Demande introuvable : " + demandeId));
    }

    // -------------------------------------------------------------------------
    // Modification (PATCH /api/v1/demandes/{id})
    // -------------------------------------------------------------------------

    /**
     * Annule une demande ouverte et/ou corrige le compteur de compensation (repli manuel
     * quand service-donneurs est injoignable).
     */
    @Transactional
    public Demande modifier(UUID demandeId, ModifierDemandeRequest corps) {
        validerCorpsModification(corps);

        Demande demande = demandeRepository.findByIdVerrouillee(demandeId)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Demande introuvable : " + demandeId));

        if (corps.statut() == StatutDemande.ANNULEE) {
            annulerDemande(demandeId, demande);
        }

        if (corps.compensationRecue() != null) {
            corrigerCompensationManuellement(demande, corps.compensationRecue());
        }

        return demandeRepository.save(demande);
    }

    private void validerCorpsModification(ModifierDemandeRequest corps) {
        if (corps.statut() == null && corps.compensationRecue() == null) {
            throw new RequeteInvalideException(
                    "Au moins un champ est obligatoire : statut ou compensationRecue");
        }
        if (corps.statut() != null && corps.statut() != StatutDemande.ANNULEE) {
            throw new RequeteInvalideException(
                    "Seul le statut ANNULEE est accepté pour une mise à jour manuelle");
        }
    }

    private void annulerDemande(UUID demandeId, Demande demande) {
        if (reservationsEnCours.contains(demandeId)) {
            throw new ConflitMetierException(
                    "La demande est en cours de réservation — réessayez dans quelques secondes");
        }
        if (!demande.getStatut().estOuverte()) {
            throw new ConflitMetierException(
                    "La demande est au statut " + demande.getStatut() + " et ne peut plus être annulée");
        }
        demande.setStatut(StatutDemande.ANNULEE);
    }

    private void corrigerCompensationManuellement(Demande demande, int nouvelleValeur) {
        if (nouvelleValeur < 0) {
            throw new RequeteInvalideException("compensationRecue ne peut pas être négative");
        }
        demande.setCompensationRecue(nouvelleValeur);
    }

    // -------------------------------------------------------------------------
    // Helpers internes
    // -------------------------------------------------------------------------

    private void validerIntervalleCompensation(Integer compensationDemandee) {
        if (compensationDemandee == null) {
            return;
        }
        boolean horsIntervalle = compensationDemandee < regles.getCompensationMin()
                || compensationDemandee > regles.getCompensationMax();
        if (horsIntervalle) {
            throw new RequeteInvalideException(
                    "compensationDemandee doit être entre %d et %d (R17)"
                            .formatted(regles.getCompensationMin(), regles.getCompensationMax()));
        }
    }

    /** Exposé au {@link ReconciliationJob} pour éviter de rejouer une demande en vol. */
    Set<UUID> getReservationsEnCours() {
        return reservationsEnCours;
    }
}
