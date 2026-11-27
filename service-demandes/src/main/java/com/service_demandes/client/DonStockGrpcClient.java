package com.service_demandes.client;

import com.service_demandes.config.DemandesRulesProperties;
import com.service_demandes.entity.Composant;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.Urgence;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tn.iit.blood.donstock.v1.ConsulterReservationRequest;
import tn.iit.blood.donstock.v1.ConsulterReservationResponse;
import tn.iit.blood.donstock.v1.DonStockServiceGrpc;
import tn.iit.blood.donstock.v1.NiveauStockMessage;
import tn.iit.blood.donstock.v1.SuivreNiveauxStockRequest;
import tn.iit.blood.donstock.v1.VerifierEtReserverRequest;
import tn.iit.blood.donstock.v1.VerifierEtReserverResponse;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Client gRPC vers service-don-stock. Active sur tous les profils sauf {@code mock}.
 *
 * <p>Chaque appel porte un deadline configurable via {@code GRPC_DEADLINE_MS}.
 * Un disjoncteur Resilience4j nommé {@code donStock} protège les deux appels unaires.
 * En cas d'ouverture du disjoncteur ou d'erreur réseau, une
 * {@link DonStockIndisponibleException} est levée : la demande reste {@code EN_ATTENTE}
 * et le job de réconciliation la reprend plus tard.</p>
 */
@Component
@Profile("!mock")
public class DonStockGrpcClient implements DonStockClient {

    private static final Logger log = LoggerFactory.getLogger(DonStockGrpcClient.class);

    private final DemandesRulesProperties config;
    private ManagedChannel channel;
    private DonStockServiceGrpc.DonStockServiceBlockingStub blockingStub;
    private DonStockServiceGrpc.DonStockServiceStub asyncStub;

    public DonStockGrpcClient(DemandesRulesProperties config) {
        this.config = config;
    }

    // -------------------------------------------------------------------------
    // Cycle de vie du canal gRPC
    // -------------------------------------------------------------------------

    @PostConstruct
    void ouvrirCanal() {
        String adresse = config.getDonStockGrpcTarget();
        log.info("Ouverture du canal gRPC vers service-don-stock : {}", adresse);
        channel = ManagedChannelBuilder.forTarget(adresse)
                .usePlaintext()
                .build();
        blockingStub = DonStockServiceGrpc.newBlockingStub(channel);
        asyncStub    = DonStockServiceGrpc.newStub(channel);
    }

    @PreDestroy
    void fermerCanal() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            log.info("Canal gRPC vers service-don-stock fermé");
        }
    }

    // -------------------------------------------------------------------------
    // VerifierEtReserver (appel 1)
    // -------------------------------------------------------------------------

    @Override
    @CircuitBreaker(name = "donStock", fallbackMethod = "quandDisjoncteurOuvertPourVerification")
    public ReservationResultat verifierEtReserver(ReservationCommande commande) {
        VerifierEtReserverRequest requete = construireRequeteVerification(commande);
        VerifierEtReserverResponse reponse = appellerVerifierEtReserver(requete);
        return traduireReponseVerification(reponse);
    }

    private VerifierEtReserverRequest construireRequeteVerification(ReservationCommande commande) {
        return VerifierEtReserverRequest.newBuilder()
                .setDemandeId(commande.demandeId().toString())
                .setLieuId(commande.lieuId())
                .setGroupeSanguin(versProtoGroupeSanguin(commande.groupeSanguin()))
                .setComposant(versProtoComposant(commande.composant()))
                .setQuantite(commande.quantite())
                .setUrgence(versProtoUrgence(commande.urgence()))
                .build();
    }

    private VerifierEtReserverResponse appellerVerifierEtReserver(VerifierEtReserverRequest requete) {
        try {
            return blockingStub
                    .withDeadlineAfter(config.getGrpcDeadlineMs(), TimeUnit.MILLISECONDS)
                    .verifierEtReserver(requete);
        } catch (StatusRuntimeException erreurGrpc) {
            log.warn("VerifierEtReserver échoué — code gRPC : {}, message : {}",
                    erreurGrpc.getStatus().getCode(), erreurGrpc.getMessage());
            throw new DonStockIndisponibleException(
                    "Don & Stock injoignable (code : " + erreurGrpc.getStatus().getCode() + ")",
                    erreurGrpc);
        }
    }

    private ReservationResultat traduireReponseVerification(VerifierEtReserverResponse reponse) {
        return new ReservationResultat(
                versDomaineStatutReservation(reponse.getStatut()),
                viderSiVide(reponse.getReference()),
                viderSiVide(reponse.getCentreSource()),
                reponse.getEtaMinutes() == 0 ? null : reponse.getEtaMinutes());
    }

    @SuppressWarnings("unused")
    private ReservationResultat quandDisjoncteurOuvertPourVerification(
            ReservationCommande commande, CallNotPermittedException erreurDisjoncteur) {
        log.warn("Disjoncteur donStock OUVERT — demande {} laissée EN_ATTENTE", commande.demandeId());
        throw new DonStockIndisponibleException("Disjoncteur ouvert vers service-don-stock", erreurDisjoncteur);
    }

    // -------------------------------------------------------------------------
    // ConsulterReservation (appel de réconciliation)
    // -------------------------------------------------------------------------

    @Override
    @CircuitBreaker(name = "donStock", fallbackMethod = "quandDisjoncteurOuvertPourConsultation")
    public Optional<ConsultationResultat> consulterReservation(UUID demandeId) {
        ConsulterReservationRequest requete = ConsulterReservationRequest.newBuilder()
                .setDemandeId(demandeId.toString())
                .build();
        try {
            ConsulterReservationResponse reponse = blockingStub
                    .withDeadlineAfter(config.getGrpcDeadlineMs(), TimeUnit.MILLISECONDS)
                    .consulterReservation(requete);
            return Optional.of(traduireReponseConsultation(reponse));
        } catch (StatusRuntimeException erreurGrpc) {
            return traiterErreurConsultation(demandeId, erreurGrpc);
        }
    }

    private ConsultationResultat traduireReponseConsultation(ConsulterReservationResponse reponse) {
        return new ConsultationResultat(
                reponse.getDemandeId(),
                versDomaineStatutReservation(reponse.getStatut()),
                viderSiVide(reponse.getReference()),
                viderSiVide(reponse.getCentreSource()),
                reponse.getEtaMinutes() == 0 ? null : reponse.getEtaMinutes());
    }

    /**
     * NOT_FOUND signifie que Don & Stock n'a jamais vu cette demande.
     * C'est un cas métier normal (la demande sera rejouée), pas une erreur technique.
     */
    private Optional<ConsultationResultat> traiterErreurConsultation(
            UUID demandeId, StatusRuntimeException erreurGrpc) {
        if (erreurGrpc.getStatus().getCode() == Status.Code.NOT_FOUND) {
            log.debug("ConsulterReservation : demande {} absente de Don & Stock (NOT_FOUND)", demandeId);
            return Optional.empty();
        }
        log.warn("ConsulterReservation échoué pour demande {} — code gRPC : {}",
                demandeId, erreurGrpc.getStatus().getCode());
        throw new DonStockIndisponibleException(
                "ConsulterReservation injoignable (code : " + erreurGrpc.getStatus().getCode() + ")",
                erreurGrpc);
    }

    @SuppressWarnings("unused")
    private Optional<ConsultationResultat> quandDisjoncteurOuvertPourConsultation(
            UUID demandeId, CallNotPermittedException erreurDisjoncteur) {
        log.warn("Disjoncteur donStock OUVERT — ConsulterReservation ignorée pour demande {}", demandeId);
        throw new DonStockIndisponibleException("Disjoncteur ouvert vers service-don-stock", erreurDisjoncteur);
    }

    // -------------------------------------------------------------------------
    // LibererReservation (best-effort, pas de disjoncteur)
    // -------------------------------------------------------------------------

    @Override
    public void libererReservation(UUID demandeId, String referenceReservation) {
        // Le proto ne définit pas d'opération de libération explicite.
        // Don & Stock gère son propre nettoyage. On trace uniquement.
        log.info("Libération signalée (best-effort) — réservation {} / demande {}",
                referenceReservation, demandeId);
    }

    // -------------------------------------------------------------------------
    // SuivreNiveauxStock (stream asynchrone, utilisé par StockBridgeService)
    // -------------------------------------------------------------------------

    /**
     * Ouvre un stream gRPC {@code SuivreNiveauxStock}.
     * Appelle {@code surChaqueMiseAJour} pour chaque niveau reçu.
     * Appelle {@code surFermetureOuErreur} quand le stream se ferme (normalement ou sur erreur),
     * ce qui permet à {@link com.service_demandes.service.StockBridgeService} de se reconnecter.
     *
     * @param lieuId                 filtre optionnel par lieu (null = aucun filtre, tous les lieux)
     * @param surChaqueMiseAJour     callback appelé pour chaque {@link NiveauStockMessage}
     * @param surFermetureOuErreur   callback appelé quand le stream se termine
     */
    public void ouvrirStreamNiveauxStock(
            String lieuId,
            Consumer<NiveauStockMessage> surChaqueMiseAJour,
            Consumer<Throwable> surFermetureOuErreur) {

        SuivreNiveauxStockRequest requete = construireRequeteSuivi(lieuId);
        asyncStub.suivreNiveauxStock(requete, new ObservateurNiveauxStock(
                surChaqueMiseAJour, surFermetureOuErreur));
    }

    private SuivreNiveauxStockRequest construireRequeteSuivi(String lieuId) {
        SuivreNiveauxStockRequest.Builder builder = SuivreNiveauxStockRequest.newBuilder();
        if (lieuId != null && !lieuId.isBlank()) {
            builder.setLieuId(lieuId);
        }
        return builder.build();
    }

    /**
     * Observateur du stream {@code SuivreNiveauxStock}.
     * Séparé en classe nommée pour respecter la règle "do one thing" et rendre le code lisible.
     */
    private static final class ObservateurNiveauxStock implements StreamObserver<NiveauStockMessage> {

        private static final Logger log = LoggerFactory.getLogger(ObservateurNiveauxStock.class);

        private final Consumer<NiveauStockMessage> surChaqueMiseAJour;
        private final Consumer<Throwable> surFermetureOuErreur;

        ObservateurNiveauxStock(Consumer<NiveauStockMessage> surChaqueMiseAJour,
                                Consumer<Throwable> surFermetureOuErreur) {
            this.surChaqueMiseAJour   = surChaqueMiseAJour;
            this.surFermetureOuErreur = surFermetureOuErreur;
        }

        @Override
        public void onNext(NiveauStockMessage niveauRecu) {
            surChaqueMiseAJour.accept(niveauRecu);
        }

        @Override
        public void onError(Throwable erreurStream) {
            log.warn("Stream SuivreNiveauxStock interrompu : {}", erreurStream.getMessage());
            surFermetureOuErreur.accept(erreurStream);
        }

        @Override
        public void onCompleted() {
            log.info("Stream SuivreNiveauxStock terminé normalement — reconnexion en cours");
            surFermetureOuErreur.accept(new RuntimeException("stream-completed"));
        }
    }

    // -------------------------------------------------------------------------
    // Mappers domaine → proto
    // -------------------------------------------------------------------------

    private tn.iit.blood.donstock.v1.GroupeSanguin versProtoGroupeSanguin(GroupeSanguin groupeSanguin) {
        return switch (groupeSanguin) {
            case A_POS  -> tn.iit.blood.donstock.v1.GroupeSanguin.A_POS;
            case A_NEG  -> tn.iit.blood.donstock.v1.GroupeSanguin.A_NEG;
            case B_POS  -> tn.iit.blood.donstock.v1.GroupeSanguin.B_POS;
            case B_NEG  -> tn.iit.blood.donstock.v1.GroupeSanguin.B_NEG;
            case AB_POS -> tn.iit.blood.donstock.v1.GroupeSanguin.AB_POS;
            case AB_NEG -> tn.iit.blood.donstock.v1.GroupeSanguin.AB_NEG;
            case O_POS  -> tn.iit.blood.donstock.v1.GroupeSanguin.O_POS;
            case O_NEG  -> tn.iit.blood.donstock.v1.GroupeSanguin.O_NEG;
        };
    }

    private tn.iit.blood.donstock.v1.Composant versProtoComposant(Composant composant) {
        return switch (composant) {
            case CGR        -> tn.iit.blood.donstock.v1.Composant.CGR;
            case PLASMA     -> tn.iit.blood.donstock.v1.Composant.PLASMA;
            case PLAQUETTES -> tn.iit.blood.donstock.v1.Composant.PLAQUETTES;
        };
    }

    private tn.iit.blood.donstock.v1.Urgence versProtoUrgence(Urgence urgence) {
        return switch (urgence) {
            case NORMALE  -> tn.iit.blood.donstock.v1.Urgence.NORMALE;
            case HAUTE    -> tn.iit.blood.donstock.v1.Urgence.HAUTE;
            case CRITIQUE -> tn.iit.blood.donstock.v1.Urgence.CRITIQUE;
        };
    }

    // -------------------------------------------------------------------------
    // Mapper proto → domaine
    // -------------------------------------------------------------------------

    StatutReservation versDomaineStatutReservation(tn.iit.blood.donstock.v1.StatutReservation statutProto) {
        return switch (statutProto) {
            case RESERVEE_STOCK_LOCAL       -> StatutReservation.RESERVEE_STOCK_LOCAL;
            case APPROVISIONNEMENT_COMMANDE -> StatutReservation.APPROVISIONNEMENT_COMMANDE;
            case STOCK_INSUFFISANT_PARTOUT  -> StatutReservation.STOCK_INSUFFISANT_PARTOUT;
            case LIVREE                     -> StatutReservation.LIVREE;
            case EN_ATTENTE_CONFIRMATION    -> StatutReservation.EN_ATTENTE_CONFIRMATION;
            default -> throw new IllegalStateException(
                    "Statut gRPC non reconnu dans le domaine métier : " + statutProto);
        };
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /** Retourne null si la chaîne proto est vide (proto3 renvoie "" par défaut). */
    private static String viderSiVide(String valeurProto) {
        return valeurProto.isEmpty() ? null : valeurProto;
    }
}
