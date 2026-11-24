package tn.iit.blood.service_don_stock.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tn.iit.blood.donstock.v1.*;
import tn.iit.blood.service_don_stock.event.StockChangeEvent;
import tn.iit.blood.service_don_stock.model.*;
import tn.iit.blood.service_don_stock.repository.*;
import tn.iit.blood.service_don_stock.service.CentreResolver;
import tn.iit.blood.service_don_stock.soap.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * gRPC service implementation — all 5 RPCs.
 *
 * S4-04/05 — VerifierEtReserver  (local path + central SOAP path)
 * S4-07    — EnregistrerPrelevement
 * S4-09    — ConsulterReservation, DelivrerCertificat
 * S4-10    — SuivreNiveauxStock (server streaming)
 * S4-12    — SOAP client (CntsWebServiceClient) fully wired.
 *
 * SOAP calls are NEVER made inside a database transaction.  Each DB step that
 * takes a SELECT FOR UPDATE lock is committed in its own short transaction
 * before the SOAP call starts (guide S4-04/S4-05, defense question 599).
 */
@GrpcService
public class DonStockGrpcService extends DonStockServiceGrpc.DonStockServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(DonStockGrpcService.class);

    private static final String[] ALL_COMPOSANTS = {"CGR", "PLASMA", "PLAQUETTES"};

    private final LieuRepository            lieuRepo;
    private final StockNiveauRepository     stockRepo;
    private final ReservationRepository     reservationRepo;
    private final PrelevementRepository     prelevementRepo;
    private final StockStreamRegistry       streamRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final CntsWebServiceClient      cntsClient;
    private final CentreResolver            centreResolver;
    /** Null in pure unit tests: callbacks there run without a transaction manager. */
    private final TransactionTemplate       txTemplate;

    @Value("${app.volume-prelevement-ml:450}")
    private int volumePrelevementMl = 450; // default also covers unit tests that don't inject @Value

    @Autowired
    public DonStockGrpcService(LieuRepository lieuRepo,
                               StockNiveauRepository stockRepo,
                               ReservationRepository reservationRepo,
                               PrelevementRepository prelevementRepo,
                               StockStreamRegistry streamRegistry,
                               ApplicationEventPublisher eventPublisher,
                               CntsWebServiceClient cntsClient,
                               CentreResolver centreResolver,
                               PlatformTransactionManager txManager) {
        this.lieuRepo        = lieuRepo;
        this.stockRepo       = stockRepo;
        this.reservationRepo = reservationRepo;
        this.prelevementRepo = prelevementRepo;
        this.streamRegistry  = streamRegistry;
        this.eventPublisher  = eventPublisher;
        this.cntsClient      = cntsClient;
        this.centreResolver  = centreResolver;
        this.txTemplate      = new TransactionTemplate(txManager);
    }

    /** Constructor for pure unit tests (no Spring context / transaction manager). */
    public DonStockGrpcService(LieuRepository lieuRepo,
                        StockNiveauRepository stockRepo,
                        ReservationRepository reservationRepo,
                        PrelevementRepository prelevementRepo,
                        StockStreamRegistry streamRegistry,
                        ApplicationEventPublisher eventPublisher,
                        CntsWebServiceClient cntsClient,
                        CentreResolver centreResolver) {
        this.lieuRepo        = lieuRepo;
        this.stockRepo       = stockRepo;
        this.reservationRepo = reservationRepo;
        this.prelevementRepo = prelevementRepo;
        this.streamRegistry  = streamRegistry;
        this.eventPublisher  = eventPublisher;
        this.cntsClient      = cntsClient;
        this.centreResolver  = centreResolver;
        this.txTemplate      = null;
    }

    /** Package-private — used by unit tests that cannot inject @Value. */
    void setVolumePrelevementMl(int v) { this.volumePrelevementMl = v; }

    /** Runs one short DB unit of work; no lock survives the callback. */
    private <T> T inTransaction(Supplier<T> work) {
        return txTemplate == null ? work.get() : txTemplate.execute(status -> work.get());
    }

    // =================================================================
    // S4-04 / S4-05 — VerifierEtReserver
    // =================================================================

    @Override
    public void verifierEtReserver(VerifierEtReserverRequest request,
                                   StreamObserver<VerifierEtReserverResponse> responseObserver) {
        try {
            // --- Validation ---
            if (request.getQuantite() < 1) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("quantite must be >= 1").asRuntimeException());
                return;
            }
            if (request.getGroupeSanguin() == GroupeSanguin.GROUPE_SANGUIN_UNSPECIFIED) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("groupe_sanguin is required").asRuntimeException());
                return;
            }
            if (request.getComposant() == Composant.COMPOSANT_UNSPECIFIED) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("composant is required").asRuntimeException());
                return;
            }
            if (request.getUrgence() == Urgence.URGENCE_UNSPECIFIED) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("urgence is required").asRuntimeException());
                return;
            }

            // --- Idempotency (before lieu lookup to short-circuit early) ---
            Optional<Reservation> existing = reservationRepo.findByDemandeId(request.getDemandeId());
            if (existing.isPresent()) {
                responseObserver.onNext(toVerifierResponse(existing.get()));
                responseObserver.onCompleted();
                return;
            }

            Lieu lieu = lieuRepo.findById(request.getLieuId()).orElse(null);
            if (lieu == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("lieu " + request.getLieuId() + " not found")
                        .asRuntimeException());
                return;
            }

            // --- Local path (S4-04) — one transaction, FOR UPDATE lock released on commit ---
            if (lieu.isStockLocal()) {
                Reservation local = inTransaction(() -> tryReserveLocally(request, lieu.getId()));
                if (local != null) {
                    responseObserver.onNext(toVerifierResponse(local));
                    responseObserver.onCompleted();
                    return;
                }
                // Local stock insufficient — fall through to central path with no lock held.
            }

            // --- Persist the pending reservation before the SOAP call (no transaction stays open) ---
            Reservation pending = inTransaction(() -> {
                Reservation r = buildReservation(request, lieu.getId());
                r.setStatut(ReservationStatuts.EN_ATTENTE_CONFIRMATION);
                return reservationRepo.save(r);
            });

            tn.iit.blood.donstock.soap.client.DemanderApprovisionnementResponse soapResp;
            try {
                soapResp = cntsClient.demanderApprovisionnement(
                        lieu.getId(),
                        lieu.getRegion(),
                        request.getGroupeSanguin().name(),
                        request.getComposant().name(),
                        request.getQuantite(),
                        request.getUrgence().name(),
                        request.getDemandeId());
            } catch (StockInsuffisantException e) {
                inTransaction(() -> {
                    pending.setStatut(StatutReservation.STOCK_INSUFFISANT_PARTOUT.name());
                    return reservationRepo.save(pending);
                });
                log.warn("Stock insuffisant partout for demande {}", request.getDemandeId());
                responseObserver.onNext(VerifierEtReserverResponse.newBuilder()
                        .setStatut(StatutReservation.STOCK_INSUFFISANT_PARTOUT)
                        .build());
                responseObserver.onCompleted();
                return;
            } catch (Exception e) {
                // CNTS down or circuit open — pending row is already EN_ATTENTE_CONFIRMATION
                // and RetryConfirmerJob will re-send it with the same demandeId.
                log.warn("CNTS unavailable for demande {}, stored as {}: {}",
                        request.getDemandeId(), ReservationStatuts.EN_ATTENTE_CONFIRMATION, e.getMessage());
                responseObserver.onError(Status.UNAVAILABLE
                        .withDescription("CNTS service unavailable; order stored for retry")
                        .asRuntimeException());
                return;
            }

            // --- Second short transaction: record the answer and increase en_transit ---
            inTransaction(() -> {
                pending.setStatut(StatutReservation.APPROVISIONNEMENT_COMMANDE.name());
                pending.setReference(soapResp.getReference());
                pending.setCentreSource(soapResp.getCentreSource());
                pending.setEtaMinutes(soapResp.getDelaiMinutes());
                reservationRepo.save(pending);

                List<StockNiveau> changed = new ArrayList<>();
                stockRepo.findByKeyForUpdate(lieu.getId(),
                        request.getGroupeSanguin().name(),
                        request.getComposant().name())
                        .ifPresent(stock -> {
                            stock.setEnTransit(stock.getEnTransit() + request.getQuantite());
                            stockRepo.save(stock);
                            changed.add(stock);
                        });
                if (!changed.isEmpty()) {
                    publishStockChange(changed);
                }
                return null;
            });

            log.info("Central order placed for demande {} via {}: ref={}",
                    request.getDemandeId(), soapResp.getCentreSource(), soapResp.getReference());
            responseObserver.onNext(VerifierEtReserverResponse.newBuilder()
                    .setStatut(StatutReservation.APPROVISIONNEMENT_COMMANDE)
                    .setReference(soapResp.getReference())
                    .setCentreSource(soapResp.getCentreSource())
                    .setEtaMinutes(soapResp.getDelaiMinutes())
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Unexpected error in verifierEtReserver", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    /** Called inside a short transaction; returns null when local stock cannot satisfy the request. */
    private Reservation tryReserveLocally(VerifierEtReserverRequest request, String lieuId) {
        Optional<StockNiveau> stockOpt = stockRepo.findByKeyForUpdate(
                lieuId,
                request.getGroupeSanguin().name(),
                request.getComposant().name());

        if (stockOpt.isEmpty() || stockOpt.get().getDisponible() < request.getQuantite()) {
            return null;
        }
        StockNiveau stock = stockOpt.get();
        stock.setDisponible(stock.getDisponible() - request.getQuantite());
        stock.setReserve(stock.getReserve() + request.getQuantite());
        stockRepo.save(stock);

        Reservation r = buildReservation(request, lieuId);
        r.setStatut(StatutReservation.RESERVEE_STOCK_LOCAL.name());
        reservationRepo.save(r);

        publishStockChange(List.of(stock));
        return r;
    }

    // =================================================================
    // S4-09 — ConsulterReservation
    // =================================================================

    @Override
    public void consulterReservation(ConsulterReservationRequest request,
                                     StreamObserver<ConsulterReservationResponse> responseObserver) {
        try {
            Reservation r = reservationRepo.findByDemandeId(request.getDemandeId()).orElse(null);
            if (r == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("reservation not found for demande " + request.getDemandeId())
                        .asRuntimeException());
                return;
            }
            responseObserver.onNext(ConsulterReservationResponse.newBuilder()
                    .setDemandeId(r.getDemandeId())
                    .setStatut(ReservationStatuts.toProto(r.getStatut()))
                    .setReference(r.getReference() != null ? r.getReference() : "")
                    .setCentreSource(r.getCentreSource() != null ? r.getCentreSource() : "")
                    .setEtaMinutes(r.getEtaMinutes() != null ? r.getEtaMinutes() : 0)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // =================================================================
    // S4-07 — EnregistrerPrelevement
    // =================================================================

    @Override
    public void enregistrerPrelevement(EnregistrerPrelevementRequest request,
                                       StreamObserver<EnregistrerPrelevementResponse> responseObserver) {
        try {
            if (request.getVolumeMl() != volumePrelevementMl) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("volume_ml must be " + volumePrelevementMl)
                        .asRuntimeException());
                return;
            }
            if (request.getGroupeSanguin() == GroupeSanguin.GROUPE_SANGUIN_UNSPECIFIED) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("groupe_sanguin is required").asRuntimeException());
                return;
            }

            // Idempotency
            Optional<Prelevement> existing = prelevementRepo.findByDonId(request.getDonId());
            if (existing.isPresent()) {
                Prelevement p = existing.get();
                if ("A_DECLARER".equals(p.getStatut())) {
                    declareToCnts(p, request.getDatePrelevement(), responseObserver);
                    return;
                }
                responseObserver.onNext(EnregistrerPrelevementResponse.newBuilder()
                        .setNumeroDonNational(p.getNumeroDonNational() != null ? p.getNumeroDonNational() : "")
                        .setStatut(p.getStatut())
                        .build());
                responseObserver.onCompleted();
                return;
            }

            // Insert as A_DECLARER in a short transaction, then call CNTS outside it.
            Prelevement p = inTransaction(() -> {
                Prelevement fresh = new Prelevement();
                fresh.setDonId(request.getDonId());
                fresh.setDonneurId(request.getDonneurId());
                fresh.setCentreId(request.getCentreId());
                fresh.setGroupeSanguin(request.getGroupeSanguin().name());
                fresh.setVolumeMl(request.getVolumeMl());
                fresh.setStatut("A_DECLARER");
                return prelevementRepo.save(fresh);
            });

            declareToCnts(p, request.getDatePrelevement(), responseObserver);

        } catch (Exception e) {
            log.error("Unexpected error in enregistrerPrelevement", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private void declareToCnts(Prelevement p, String datePrelevement,
                               StreamObserver<tn.iit.blood.donstock.v1.EnregistrerPrelevementResponse> responseObserver) {
        LocalDate date;
        try {
            date = parseIsoDate(datePrelevement, "date_prelevement");
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage()).asRuntimeException());
            return;
        }

        // A mobile collection feeds the CRTS of its region, both at CNTS and in stock_niveau.
        String stockCentreId = centreResolver.resolveStockCentreId(p.getCentreId());

        tn.iit.blood.donstock.soap.client.EnregistrerPrelevementResponse soapResp;
        try {
            soapResp = cntsClient.enregistrerPrelevement(
                    p.getDonId(),
                    p.getGroupeSanguin(),
                    p.getVolumeMl(),
                    stockCentreId,
                    date);
        } catch (PrelevementInvalideException e) {
            log.warn("PrelevementInvalide for don {}: {}", p.getDonId(), e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage()).asRuntimeException());
            return;
        } catch (Exception e) {
            // CNTS down — stays A_DECLARER, Donneurs' retry job will call again.
            log.warn("CNTS unavailable for don {}, staying A_DECLARER: {}", p.getDonId(), e.getMessage());
            responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("CNTS service unavailable; will retry later")
                    .asRuntimeException());
            return;
        }

        inTransaction(() -> {
            p.setNumeroDonNational(soapResp.getNumeroDonNational());
            p.setStatut("QUALIFICATION_EN_COURS");
            prelevementRepo.save(p);

            List<StockNiveau> changed = new ArrayList<>();
            for (String composant : ALL_COMPOSANTS) {
                stockRepo.findByKeyForUpdate(stockCentreId, p.getGroupeSanguin(), composant)
                        .ifPresent(stock -> {
                            stock.setEnQualification(stock.getEnQualification() + 1);
                            stockRepo.save(stock);
                            changed.add(stock);
                        });
            }
            if (!changed.isEmpty()) {
                publishStockChange(changed);
            }
            return null;
        });

        log.info("Prelevement declared to CNTS: don={} → numeroDonNational={} (stock centre={})",
                p.getDonId(), soapResp.getNumeroDonNational(), stockCentreId);
        responseObserver.onNext(EnregistrerPrelevementResponse.newBuilder()
                .setNumeroDonNational(soapResp.getNumeroDonNational())
                .setStatut("QUALIFICATION_EN_COURS")
                .build());
        responseObserver.onCompleted();
    }

    // =================================================================
    // S4-09 — DelivrerCertificat
    // =================================================================

    @Override
    public void delivrerCertificat(DelivrerCertificatRequest request,
                                   StreamObserver<DelivrerCertificatResponse> responseObserver) {
        try {
            Prelevement p = prelevementRepo.findByDonId(request.getDonId()).orElse(null);
            if (p == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("prelevement not found for don " + request.getDonId())
                        .asRuntimeException());
                return;
            }
            if (p.getNumeroDonNational() == null || p.getNumeroDonNational().isBlank()) {
                responseObserver.onError(Status.FAILED_PRECONDITION
                        .withDescription("No numero_don_national yet for don " + request.getDonId())
                        .asRuntimeException());
                return;
            }

            // Idempotency (R25) — same don always gets same certificate
            if (p.getNumeroCertificat() != null) {
                responseObserver.onNext(DelivrerCertificatResponse.newBuilder()
                        .setNumeroCertificat(p.getNumeroCertificat())
                        .setDateEmission(p.getDateCertificat() != null ? p.getDateCertificat().toString() : "")
                        .setStatut("VALIDE")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            LocalDate dateDon;
            try {
                dateDon = parseIsoDate(request.getDateDon(), "date_don");
            } catch (IllegalArgumentException e) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription(e.getMessage()).asRuntimeException());
                return;
            }

            String stockCentreId = centreResolver.resolveStockCentreId(p.getCentreId());

            tn.iit.blood.donstock.soap.client.DelivrerCertificatDonResponse soapResp;
            try {
                soapResp = cntsClient.delivrerCertificatDon(
                        p.getDonId(),
                        p.getNumeroDonNational(),
                        request.getDonneurRef(),
                        stockCentreId,
                        dateDon);
            } catch (CertificatRefuseException e) {
                log.warn("CertificatRefuse for don {}: {}", p.getDonId(), e.getMessage());
                responseObserver.onError(Status.FAILED_PRECONDITION
                        .withDescription(e.getMessage()).asRuntimeException());
                return;
            } catch (Exception e) {
                log.warn("CNTS unavailable for certificate of don {}: {}", p.getDonId(), e.getMessage());
                responseObserver.onError(Status.UNAVAILABLE
                        .withDescription("CNTS service unavailable; certificate will be retried")
                        .asRuntimeException());
                return;
            }

            LocalDate dateEmission = toLocalDate(soapResp.getDateEmission());
            inTransaction(() -> {
                p.setNumeroCertificat(soapResp.getNumeroCertificat());
                p.setDateCertificat(dateEmission);
                prelevementRepo.save(p);
                return null;
            });

            log.info("Certificate issued for don {}: {}", p.getDonId(), soapResp.getNumeroCertificat());
            responseObserver.onNext(DelivrerCertificatResponse.newBuilder()
                    .setNumeroCertificat(soapResp.getNumeroCertificat())
                    .setDateEmission(dateEmission != null ? dateEmission.toString() : "")
                    .setStatut("VALIDE")
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // =================================================================
    // S4-10 — SuivreNiveauxStock (server streaming)
    // =================================================================

    @Override
    public void suivreNiveauxStock(SuivreNiveauxStockRequest request,
                                   StreamObserver<NiveauStockMessage> responseObserver) {
        String subscriberId = streamRegistry.register(request, responseObserver);

        String lieuFilter  = request.getLieuId().isBlank() ? null : request.getLieuId();
        String groupFilter = request.getGroupeSanguin().getNumber() == 0 ? null
                           : request.getGroupeSanguin().name();

        List<StockNiveau> snapshot = stockRepo.findByFilters(lieuFilter, groupFilter);
        for (StockNiveau s : snapshot) {
            Lieu lieu = lieuRepo.findById(s.getLieuId()).orElse(null);
            try {
                responseObserver.onNext(StockNiveauMapper.toProto(s, lieu));
            } catch (Exception e) {
                log.warn("Failed to send snapshot message: {}", e.getMessage());
                streamRegistry.unregister(subscriberId);
                return;
            }
        }
        log.info("Sent initial snapshot of {} rows to new subscriber", snapshot.size());
    }

    // =================================================================
    // Helpers
    // =================================================================

    private void publishStockChange(List<StockNiveau> rows) {
        eventPublisher.publishEvent(new StockChangeEvent(this, rows));
    }

    private Reservation buildReservation(VerifierEtReserverRequest request, String lieuId) {
        Reservation r = new Reservation();
        r.setDemandeId(request.getDemandeId());
        r.setLieuId(lieuId);
        r.setGroupeSanguin(request.getGroupeSanguin().name());
        r.setComposant(request.getComposant().name());
        r.setQuantite(request.getQuantite());
        return r;
    }

    private VerifierEtReserverResponse toVerifierResponse(Reservation r) {
        return VerifierEtReserverResponse.newBuilder()
                .setStatut(ReservationStatuts.toProto(r.getStatut()))
                .setReference(r.getReference() != null ? r.getReference() : "")
                .setCentreSource(r.getCentreSource() != null ? r.getCentreSource() : "")
                .setEtaMinutes(r.getEtaMinutes() != null ? r.getEtaMinutes() : 0)
                .build();
    }

    /** ISO-8601 date (yyyy-MM-dd), offset date-time or UTC instant → local date. */
    private LocalDate parseIsoDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toLocalDate();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return Instant.parse(value).atZone(ZoneOffset.UTC).toLocalDate();
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException(fieldName + " must be an ISO-8601 date");
                }
            }
        }
    }

    private LocalDate toLocalDate(javax.xml.datatype.XMLGregorianCalendar cal) {
        if (cal == null) return null;
        return LocalDate.of(cal.getYear(), cal.getMonth(), cal.getDay());
    }
}
