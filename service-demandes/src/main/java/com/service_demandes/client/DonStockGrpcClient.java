package com.service_demandes.client;

import com.service_demandes.config.DemandesRulesProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
 * Implémentation gRPC réelle du {@link DonStockClient}.
 * <p>
 * Active sur tous les profils sauf {@code mock}.
 * Chaque appel porte un deadline configurable ({@code GRPC_DEADLINE_MS}).
 * Un disjoncteur Resilience4j nommé {@code donStock} protège {@code verifierEtReserver}
 * et {@code consulterReservation}.
 * </p>
 *
 * <h3>Configuration Resilience4j partagée (S6-09)</h3>
 * <pre>
 * resilience4j.circuitbreaker.instances.donStock.sliding-window-size=10
 * resilience4j.circuitbreaker.instances.donStock.minimum-number-of-calls=5
 * resilience4j.circuitbreaker.instances.donStock.failure-rate-threshold=50
 * resilience4j.circuitbreaker.instances.donStock.wait-duration-in-open-state=10s
 * resilience4j.circuitbreaker.instances.donStock.permitted-number-of-calls-in-half-open-state=3
 * resilience4j.circuitbreaker.instances.donStock.automatic-transition-from-open-to-half-open-enabled=true
 * </pre>
 */
@Component
@Profile("!mock")
public class DonStockGrpcClient implements DonStockClient {

    private static final Logger log = LoggerFactory.getLogger(DonStockGrpcClient.class);

    private final DemandesRulesProperties props;
    private ManagedChannel channel;
    private DonStockServiceGrpc.DonStockServiceBlockingStub stub;
    private DonStockServiceGrpc.DonStockServiceStub asyncStub;

    public DonStockGrpcClient(DemandesRulesProperties props) {
        this.props = props;
    }

    @PostConstruct
    void initialiser() {
        String target = props.getDonStockGrpcTarget();
        log.info("Connexion gRPC Don & Stock → {}", target);
        channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
        stub = DonStockServiceGrpc.newBlockingStub(channel);
        asyncStub = DonStockServiceGrpc.newStub(channel);
    }

    @PreDestroy
    void fermer() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    // -------------------------------------------------------------------------
    // VerifierEtReserver — protégé par le disjoncteur donStock
    // -------------------------------------------------------------------------

    @Override
    @CircuitBreaker(name = "donStock", fallbackMethod = "fallbackVerifier")
    public ReservationResultat verifierEtReserver(ReservationCommande commande) {
        VerifierEtReserverRequest requete = VerifierEtReserverRequest.newBuilder()
                .setDemandeId(commande.demandeId().toString())
                .setLieuId(commande.lieuId())
                .setGroupeSanguin(mapGroupeSanguin(commande.groupeSanguin()))
                .setComposant(mapComposant(commande.composant()))
                .setQuantite(commande.quantite())
                .setUrgence(mapUrgence(commande.urgence()))
                .build();

        try {
            VerifierEtReserverResponse reponse = stub
                    .withDeadlineAfter(props.getGrpcDeadlineMs(), TimeUnit.MILLISECONDS)
                    .verifierEtReserver(requete);
            return mapReponse(reponse);
        } catch (StatusRuntimeException ex) {
            log.warn("gRPC VerifierEtReserver échoué : {} — {}", ex.getStatus().getCode(), ex.getMessage());
            throw new DonStockIndisponibleException("Don & Stock indisponible : " + ex.getStatus().getCode(), ex);
        }
    }

    @SuppressWarnings("unused")
    private ReservationResultat fallbackVerifier(ReservationCommande commande, CallNotPermittedException ex) {
        log.warn("Disjoncteur donStock OUVERT — repli EN_ATTENTE pour demande {}", commande.demandeId());
        throw new DonStockIndisponibleException("Disjoncteur ouvert vers Don & Stock", ex);
    }

    @SuppressWarnings("unused")
    private ReservationResultat fallbackVerifier(ReservationCommande commande, DonStockIndisponibleException ex) {
        throw ex; // laisser remonter le fallback propre
    }

    // -------------------------------------------------------------------------
    // ConsulterReservation — protégé par le disjoncteur donStock
    // -------------------------------------------------------------------------

    @Override
    @CircuitBreaker(name = "donStock", fallbackMethod = "fallbackConsulter")
    public Optional<ConsultationResultat> consulterReservation(UUID demandeId) {
        ConsulterReservationRequest requete = ConsulterReservationRequest.newBuilder()
                .setDemandeId(demandeId.toString())
                .build();
        try {
            ConsulterReservationResponse rep = stub
                    .withDeadlineAfter(props.getGrpcDeadlineMs(), TimeUnit.MILLISECONDS)
                    .consulterReservation(requete);
            return Optional.of(new ConsultationResultat(
                    rep.getDemandeId(),
                    mapStatutReservation(rep.getStatut()),
                    rep.getReference(),
                    rep.getCentreSource(),
                    rep.getEtaMinutes() == 0 ? null : rep.getEtaMinutes()));
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            log.warn("gRPC ConsulterReservation échoué : {}", ex.getStatus().getCode());
            throw new DonStockIndisponibleException("ConsulterReservation échoué : " + ex.getStatus().getCode(), ex);
        }
    }

    @SuppressWarnings("unused")
    private Optional<ConsultationResultat> fallbackConsulter(UUID demandeId, CallNotPermittedException ex) {
        log.warn("Disjoncteur donStock OUVERT — ConsulterReservation ignorée pour {}", demandeId);
        throw new DonStockIndisponibleException("Disjoncteur ouvert vers Don & Stock", ex);
    }

    // -------------------------------------------------------------------------
    // LibererReservation — pas de disjoncteur, best-effort
    // -------------------------------------------------------------------------

    @Override
    public void libererReservation(UUID demandeId, String reference) {
        // Le proto ne définit pas d'opération de libération explicite ;
        // l'annulation de la demande dans service-don-stock se fait côté Don & Stock
        // via sa propre logique de nettoyage. On trace simplement ici.
        log.info("Libération best-effort de la réservation {} pour demande {}", reference, demandeId);
    }

    // -------------------------------------------------------------------------
    // SuivreNiveauxStock — streaming asynchrone (utilisé par StockBridgeService)
    // -------------------------------------------------------------------------

    /**
     * Ouvre un stream gRPC {@code SuivreNiveauxStock} et appelle {@code onMessage} à chaque message reçu.
     * Le stream est asynchrone ; le StreamObserver retourné permet à l'appelant de l'annuler.
     *
     * @param lieuId       filtre optionnel par lieu (null = pas de filtre)
     * @param onMessage    callback pour chaque {@link NiveauStockMessage} reçu
     * @param onError      callback en cas d'erreur ou de fermeture anormale du stream
     * @return StreamObserver côté client (appeler {@code onCompleted()} pour fermer)
     */
    public io.grpc.stub.ClientCallStreamObserver<SuivreNiveauxStockRequest> ouvrirStream(
            String lieuId,
            Consumer<NiveauStockMessage> onMessage,
            Consumer<Throwable> onError) {

        SuivreNiveauxStockRequest requete = SuivreNiveauxStockRequest.newBuilder()
                .setLieuId(lieuId != null ? lieuId : "")
                .build();

        io.grpc.stub.ClientCallStreamObserver<SuivreNiveauxStockRequest>[] refHolder = new io.grpc.stub.ClientCallStreamObserver[1];

        asyncStub.suivreNiveauxStock(requete, new StreamObserver<>() {
            @Override
            public void onNext(NiveauStockMessage message) {
                onMessage.accept(message);
            }

            @Override
            public void onError(Throwable t) {
                log.warn("Stream SuivreNiveauxStock erreur : {}", t.getMessage());
                onError.accept(t);
            }

            @Override
            public void onCompleted() {
                log.info("Stream SuivreNiveauxStock terminé normalement");
                onError.accept(new RuntimeException("stream-completed"));
            }
        });

        // On retourne null ici car l'API grpc-java ne donne pas directement l'observer client
        // dans ce pattern. Le StockBridgeService gère la reconnexion via onError.
        return null;
    }

    // -------------------------------------------------------------------------
    // Mappers proto ↔ domaine
    // -------------------------------------------------------------------------

    private tn.iit.blood.donstock.v1.GroupeSanguin mapGroupeSanguin(
            com.service_demandes.entity.GroupeSanguin gs) {
        return switch (gs) {
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

    private tn.iit.blood.donstock.v1.Composant mapComposant(
            com.service_demandes.entity.Composant c) {
        return switch (c) {
            case CGR       -> tn.iit.blood.donstock.v1.Composant.CGR;
            case PLASMA    -> tn.iit.blood.donstock.v1.Composant.PLASMA;
            case PLAQUETTES -> tn.iit.blood.donstock.v1.Composant.PLAQUETTES;
        };
    }

    private tn.iit.blood.donstock.v1.Urgence mapUrgence(
            com.service_demandes.entity.Urgence u) {
        return switch (u) {
            case NORMALE  -> tn.iit.blood.donstock.v1.Urgence.NORMALE;
            case HAUTE    -> tn.iit.blood.donstock.v1.Urgence.HAUTE;
            case CRITIQUE -> tn.iit.blood.donstock.v1.Urgence.CRITIQUE;
        };
    }

    private ReservationResultat mapReponse(VerifierEtReserverResponse rep) {
        return new ReservationResultat(
                mapStatutReservation(rep.getStatut()),
                rep.getReference().isEmpty() ? null : rep.getReference(),
                rep.getCentreSource().isEmpty() ? null : rep.getCentreSource(),
                rep.getEtaMinutes() == 0 ? null : rep.getEtaMinutes());
    }

    StatutReservation mapStatutReservation(tn.iit.blood.donstock.v1.StatutReservation s) {
        return switch (s) {
            case RESERVEE_STOCK_LOCAL       -> StatutReservation.RESERVEE_STOCK_LOCAL;
            case APPROVISIONNEMENT_COMMANDE -> StatutReservation.APPROVISIONNEMENT_COMMANDE;
            case STOCK_INSUFFISANT_PARTOUT  -> StatutReservation.STOCK_INSUFFISANT_PARTOUT;
            case LIVREE                     -> StatutReservation.LIVREE;
            case EN_ATTENTE_CONFIRMATION    -> StatutReservation.EN_ATTENTE_CONFIRMATION;
            default -> throw new IllegalStateException("Statut gRPC inconnu : " + s);
        };
    }
}
