package tn.iit.blood.service_don_stock.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import tn.iit.blood.donstock.v1.*;
import tn.iit.blood.service_don_stock.event.StockChangeEvent;
import tn.iit.blood.service_don_stock.model.*;
import tn.iit.blood.service_don_stock.repository.*;

import java.util.List;
import java.util.Optional;

/**
 * gRPC service implementation.
 *
 * S4-04 – VerifierEtReserver (local path)
 * S4-05 – VerifierEtReserver (central path stub — SOAP call wired in S4-12)
 * S4-07 – EnregistrerPrelevement
 * S4-09 – ConsulterReservation, DelivrerCertificat
 * S4-10 – SuivreNiveauxStock (server streaming)
 */
@GrpcService
public class DonStockGrpcService extends DonStockServiceGrpc.DonStockServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(DonStockGrpcService.class);

    private final LieuRepository         lieuRepo;
    private final StockNiveauRepository  stockRepo;
    private final ReservationRepository  reservationRepo;
    private final PrelevementRepository  prelevementRepo;
    private final StockStreamRegistry    streamRegistry;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.volume-prelevement-ml:450}")
    private int volumePrelevementMl;

    @Autowired
    public DonStockGrpcService(LieuRepository lieuRepo,
                               StockNiveauRepository stockRepo,
                               ReservationRepository reservationRepo,
                               PrelevementRepository prelevementRepo,
                               StockStreamRegistry streamRegistry,
                               ApplicationEventPublisher eventPublisher) {
        this.lieuRepo       = lieuRepo;
        this.stockRepo      = stockRepo;
        this.reservationRepo= reservationRepo;
        this.prelevementRepo= prelevementRepo;
        this.streamRegistry = streamRegistry;
        this.eventPublisher = eventPublisher;
    }

    // =================================================================
    // S4-04 / S4-05 – VerifierEtReserver
    // =================================================================

    @Override
    @Transactional
    public void verifierEtReserver(VerifierEtReserverRequest request,
                                   StreamObserver<VerifierEtReserverResponse> responseObserver) {
        try {
            // --- Basic validation (S4-04 step 1) ---
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

            Lieu lieu = lieuRepo.findById(request.getLieuId()).orElse(null);
            if (lieu == null) {
                responseObserver.onError(Status.NOT_FOUND
                    .withDescription("lieu " + request.getLieuId() + " not found").asRuntimeException());
                return;
            }

            // --- Idempotency (S4-04 step 2) ---
            Optional<Reservation> existing = reservationRepo.findByDemandeId(request.getDemandeId());
            if (existing.isPresent()) {
                Reservation r = existing.get();
                responseObserver.onNext(buildReservationResponse(r));
                responseObserver.onCompleted();
                return;
            }

            // --- Local path (S4-04 step 3) ---
            if (lieu.isStockLocal()) {
                Optional<StockNiveau> stockOpt = stockRepo.findByKeyForUpdate(
                    lieu.getId(),
                    request.getGroupeSanguin().name(),
                    request.getComposant().name()
                );

                if (stockOpt.isPresent()) {
                    StockNiveau stock = stockOpt.get();
                    if (stock.getDisponible() >= request.getQuantite()) {
                        // Reserve locally
                        stock.setDisponible(stock.getDisponible() - request.getQuantite());
                        stock.setReserve(stock.getReserve() + request.getQuantite());
                        stockRepo.save(stock);

                        Reservation reservation = new Reservation();
                        reservation.setDemandeId(request.getDemandeId());
                        reservation.setLieuId(lieu.getId());
                        reservation.setGroupeSanguin(request.getGroupeSanguin().name());
                        reservation.setComposant(request.getComposant().name());
                        reservation.setQuantite(request.getQuantite());
                        reservation.setStatut(StatutReservation.RESERVEE_STOCK_LOCAL.name());
                        reservationRepo.save(reservation);

                        // Publish stock change event (for streaming – S4-10)
                        publishStockChange(List.of(stock));

                        responseObserver.onNext(VerifierEtReserverResponse.newBuilder()
                            .setStatut(StatutReservation.RESERVEE_STOCK_LOCAL)
                            .build());
                        responseObserver.onCompleted();
                        return;
                    }
                    // Local stock insufficient → fall through to central path
                }
            }

            // --- Central path (S4-05) – SOAP call delegated to CntsService ---
            // The SOAP client is injected in S4-12; for now we store A_CONFIRMER
            // so the retry job can re-send when the SOAP client is available.
            Reservation pending = new Reservation();
            pending.setDemandeId(request.getDemandeId());
            pending.setLieuId(lieu.getId());
            pending.setGroupeSanguin(request.getGroupeSanguin().name());
            pending.setComposant(request.getComposant().name());
            pending.setQuantite(request.getQuantite());
            pending.setStatut("A_CONFIRMER");
            reservationRepo.save(pending);

            log.info("Central path needed for demande {}, stored as A_CONFIRMER", request.getDemandeId());

            responseObserver.onError(Status.UNAVAILABLE
                .withDescription("Central SOAP service not yet available; order stored as A_CONFIRMER")
                .asRuntimeException());

        } catch (Exception e) {
            log.error("Unexpected error in verifierEtReserver", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // =================================================================
    // S4-09 – ConsulterReservation
    // =================================================================

    @Override
    public void consulterReservation(ConsulterReservationRequest request,
                                     StreamObserver<ConsulterReservationResponse> responseObserver) {
        try {
            Reservation r = reservationRepo.findByDemandeId(request.getDemandeId())
                .orElse(null);
            if (r == null) {
                responseObserver.onError(Status.NOT_FOUND
                    .withDescription("reservation not found for demande " + request.getDemandeId())
                    .asRuntimeException());
                return;
            }
            responseObserver.onNext(ConsulterReservationResponse.newBuilder()
                .setDemandeId(r.getDemandeId())
                .setStatut(parseStatutReservation(r.getStatut()))
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
    // S4-07 – EnregistrerPrelevement
    // =================================================================

    @Override
    @Transactional
    public void enregistrerPrelevement(EnregistrerPrelevementRequest request,
                                       StreamObserver<EnregistrerPrelevementResponse> responseObserver) {
        try {
            // Validation
            if (request.getVolumeMl() != volumePrelevementMl) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("volume_ml must be " + volumePrelevementMl).asRuntimeException());
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
                    // Earlier SOAP failure – retry handled by declareToCnts()
                    declareToCnts(p, responseObserver);
                    return;
                }
                responseObserver.onNext(EnregistrerPrelevementResponse.newBuilder()
                    .setNumeroDonNational(p.getNumeroDonNational() != null ? p.getNumeroDonNational() : "")
                    .setStatut(p.getStatut())
                    .build());
                responseObserver.onCompleted();
                return;
            }

            // Insert as A_DECLARER then call CNTS
            Prelevement p = new Prelevement();
            p.setDonId(request.getDonId());
            p.setDonneurId(request.getDonneurId());
            p.setCentreId(request.getCentreId());
            p.setGroupeSanguin(request.getGroupeSanguin().name());
            p.setVolumeMl(request.getVolumeMl());
            p.setStatut("A_DECLARER");
            prelevementRepo.save(p);

            declareToCnts(p, responseObserver);

        } catch (Exception e) {
            log.error("Unexpected error in enregistrerPrelevement", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    /**
     * Calls SOAP enregistrerPrelevement (wired in S4-12).
     * For now, marks the row UNAVAILABLE so Donneurs' retry job can call again.
     */
    private void declareToCnts(Prelevement p,
                                StreamObserver<EnregistrerPrelevementResponse> responseObserver) {
        // The real SOAP call will be injected in S4-12.
        // Until then, the row stays A_DECLARER and we return UNAVAILABLE.
        log.warn("SOAP client not yet available for don {}, staying A_DECLARER", p.getDonId());
        responseObserver.onError(Status.UNAVAILABLE
            .withDescription("CNTS SOAP service not yet configured").asRuntimeException());
    }

    // =================================================================
    // S4-09 – DelivrerCertificat
    // =================================================================

    @Override
    @Transactional
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
            // Idempotency (R25)
            if (p.getNumeroCertificat() != null) {
                responseObserver.onNext(DelivrerCertificatResponse.newBuilder()
                    .setNumeroCertificat(p.getNumeroCertificat())
                    .setDateEmission(p.getDateCertificat() != null ? p.getDateCertificat().toString() : "")
                    .setStatut("VALIDE")
                    .build());
                responseObserver.onCompleted();
                return;
            }

            // SOAP call delegated to S4-12
            log.warn("SOAP client not yet available for certificate of don {}", request.getDonId());
            responseObserver.onError(Status.UNAVAILABLE
                .withDescription("CNTS SOAP service not yet configured").asRuntimeException());

        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // =================================================================
    // S4-10 – SuivreNiveauxStock (server streaming)
    // =================================================================

    @Override
    public void suivreNiveauxStock(SuivreNiveauxStockRequest request,
                                   StreamObserver<NiveauStockMessage> responseObserver) {
        // Register subscriber
        streamRegistry.register(request, responseObserver);

        // Send current snapshot
        String lieuFilter  = request.getLieuId().isBlank() ? null : request.getLieuId();
        String groupFilter = request.getGroupeSanguin().getNumber() == 0 ? null
                           : request.getGroupeSanguin().name();

        List<StockNiveau> snapshot = stockRepo.findByFilters(lieuFilter, groupFilter);
        for (StockNiveau s : snapshot) {
            Lieu lieu = lieuRepo.findById(s.getLieuId()).orElse(null);
            NiveauStockMessage msg = StockNiveauMapper.toProto(s, lieu);
            try {
                responseObserver.onNext(msg);
            } catch (Exception e) {
                log.warn("Failed to send snapshot message: {}", e.getMessage());
                return;
            }
        }
        log.info("Sent initial snapshot of {} rows to new subscriber", snapshot.size());
        // Stream stays open; changes arrive via StockChangeEventListener
    }

    // =================================================================
    // Helpers
    // =================================================================

    private void publishStockChange(List<StockNiveau> rows) {
        eventPublisher.publishEvent(new StockChangeEvent(this, rows));
    }

    private VerifierEtReserverResponse buildReservationResponse(Reservation r) {
        return VerifierEtReserverResponse.newBuilder()
            .setStatut(parseStatutReservation(r.getStatut()))
            .setReference(r.getReference() != null ? r.getReference() : "")
            .setCentreSource(r.getCentreSource() != null ? r.getCentreSource() : "")
            .setEtaMinutes(r.getEtaMinutes() != null ? r.getEtaMinutes() : 0)
            .build();
    }

    private StatutReservation parseStatutReservation(String name) {
        try { return StatutReservation.valueOf(name); }
        catch (IllegalArgumentException e) { return StatutReservation.STATUT_RESERVATION_UNSPECIFIED; }
    }
}
