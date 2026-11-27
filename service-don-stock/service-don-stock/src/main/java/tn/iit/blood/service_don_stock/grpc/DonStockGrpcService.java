package tn.iit.blood.service_don_stock.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import tn.iit.blood.donstock.v1.*;
import tn.iit.blood.service_don_stock.event.StockChangeEvent;
import tn.iit.blood.service_don_stock.model.*;
import tn.iit.blood.service_don_stock.repository.*;
import tn.iit.blood.service_don_stock.soap.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * gRPC service implementation — all 5 RPCs.
 *
 * S4-04/05 — VerifierEtReserver  (local path + central SOAP path)
 * S4-07    — EnregistrerPrelevement
 * S4-09    — ConsulterReservation, DelivrerCertificat
 * S4-10    — SuivreNiveauxStock (server streaming)
 *
 * S4-12    — SOAP client (CntsWebServiceClient) fully wired.
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

    @Value("${app.volume-prelevement-ml:450}")
    private int volumePrelevementMl = 450; // default also covers unit tests that don't inject @Value

    public DonStockGrpcService(LieuRepository lieuRepo,
                               StockNiveauRepository stockRepo,
                               ReservationRepository reservationRepo,
                               PrelevementRepository prelevementRepo,
                               StockStreamRegistry streamRegistry,
                               ApplicationEventPublisher eventPublisher,
                               CntsWebServiceClient cntsClient) {
        this.lieuRepo        = lieuRepo;
        this.stockRepo       = stockRepo;
        this.reservationRepo = reservationRepo;
        this.prelevementRepo = prelevementRepo;
        this.streamRegistry  = streamRegistry;
        this.eventPublisher  = eventPublisher;
        this.cntsClient      = cntsClient;
    }

    /** Package-private — used by unit tests that cannot inject @Value. */
    void setVolumePrelevementMl(int v) { this.volumePrelevementMl = v; }    // =================================================================
    // S4-04 / S4-05 — VerifierEtReserver
    // =================================================================

    @Override
    @Transactional
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
                responseObserver.onNext(toReservationResponse(existing.get()));
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

            // --- Local path (S4-04) ---
            if (lieu.isStockLocal()) {
                Optional<StockNiveau> stockOpt = stockRepo.findByKeyForUpdate(
                        lieu.getId(),
                        request.getGroupeSanguin().name(),
                        request.getComposant().name());

                if (stockOpt.isPresent()) {
                    StockNiveau stock = stockOpt.get();
                    if (stock.getDisponible() >= request.getQuantite()) {
                        stock.setDisponible(stock.getDisponible() - request.getQuantite());
                        stock.setReserve(stock.getReserve() + request.getQuantite());
                        stockRepo.save(stock);

                        Reservation r = buildReservation(request, lieu.getId());
                        r.setStatut(StatutReservation.RESERVEE_STOCK_LOCAL.name());
                        reservationRepo.save(r);

                        publishStockChange(List.of(stock));

                        responseObserver.onNext(VerifierEtReserverResponse.newBuilder()
                                .setStatut(StatutReservation.RESERVEE_STOCK_LOCAL)
                                .build());
                        responseObserver.onCompleted();
                        return;
                    }
                }
                // Local stock insufficient — fall through to central path
            }

            // --- Central path (S4-05) — SOAP demanderApprovisionnement ---
            Reservation pending = buildReservation(request, lieu.getId());

            try {
                tn.iit.blood.donstock.soap.client.DemanderApprovisionnementResponse soapResp =
                        cntsClient.demanderApprovisionnement(
                        lieu.getId(),
                        lieu.getRegion(),
                        request.getGroupeSanguin().name(),
                        request.getComposant().name(),
                        request.getQuantite(),
                        request.getUrgence().name(),
                        request.getDemandeId());

                pending.setStatut(StatutReservation.APPROVISIONNEMENT_COMMANDE.name());
                pending.setReference(soapResp.getReference());
                pending.setCentreSource(soapResp.getCentreSource());
                pending.setEtaMinutes(soapResp.getDelaiMinutes());
                reservationRepo.save(pending);

                // Increase en_transit at the hospital place
                stockRepo.findByKeyForUpdate(lieu.getId(),
                        request.getGroupeSanguin().name(),
                        request.getComposant().name())
                        .ifPresent(stock -> {
                            stock.setEnTransit(stock.getEnTransit() + request.getQuantite());
                            stockRepo.save(stock);
                            publishStockChange(List.of(stock));
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

            } catch (StockInsuffisantException e) {
                pending.setStatut(StatutReservation.STOCK_INSUFFISANT_PARTOUT.name());
                reservationRepo.save(pending);
                log.warn("Stock insuffisant partout for demande {}", request.getDemandeId());

                responseObserver.onNext(VerifierEtReserverResponse.newBuilder()
                        .setStatut(StatutReservation.STOCK_INSUFFISANT_PARTOUT)
                        .build());
                responseObserver.onCompleted();

            } catch (Exception e) {
                // CNTS down or circuit open — store as A_CONFIRMER, retry job will re-send
                pending.setStatut("A_CONFIRMER");
                reservationRepo.save(pending);
                log.warn("CNTS unavailable for demande {}, stored as A_CONFIRMER: {}",
                        request.getDemandeId(), e.getMessage());

                responseObserver.onError(Status.UNAVAILABLE
                        .withDescription("CNTS service unavailable; order stored for retry")
                        .asRuntimeException());
            }

        } catch (Exception e) {
            log.error("Unexpected error in verifierEtReserver", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
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
    // S4-07 — EnregistrerPrelevement
    // =================================================================

    @Override
    @Transactional
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

    private void declareToCnts(Prelevement p,
                                StreamObserver<tn.iit.blood.donstock.v1.EnregistrerPrelevementResponse> responseObserver) {
        try {
            tn.iit.blood.donstock.soap.client.EnregistrerPrelevementResponse soapResp =
                    cntsClient.enregistrerPrelevement(
                    p.getDonId(),
                    p.getGroupeSanguin(),
                    p.getVolumeMl(),
                    p.getCentreId(),
                    LocalDate.now());

            p.setNumeroDonNational(soapResp.getNumeroDonNational());
            p.setStatut("QUALIFICATION_EN_COURS");
            prelevementRepo.save(p);

            // Increment en_qualification for each component at the collecting centre
            List<StockNiveau> changed = new ArrayList<>();
            for (String composant : ALL_COMPOSANTS) {
                stockRepo.findByKeyForUpdate(p.getCentreId(), p.getGroupeSanguin(), composant)
                        .ifPresent(stock -> {
                            stock.setEnQualification(stock.getEnQualification() + 1);
                            stockRepo.save(stock);
                            changed.add(stock);
                        });
            }
            if (!changed.isEmpty()) {
                publishStockChange(changed);
            }

            log.info("Prelevement declared to CNTS: don={} → numeroDonNational={}",
                    p.getDonId(), soapResp.getNumeroDonNational());

            responseObserver.onNext(EnregistrerPrelevementResponse.newBuilder()
                    .setNumeroDonNational(soapResp.getNumeroDonNational())
                    .setStatut("QUALIFICATION_EN_COURS")
                    .build());
            responseObserver.onCompleted();

        } catch (PrelevementInvalideException e) {
            log.warn("PrelevementInvalide for don {}: {}", p.getDonId(), e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage()).asRuntimeException());

        } catch (Exception e) {
            // CNTS down — stays A_DECLARER, Donneurs' retry job will call again
            log.warn("CNTS unavailable for don {}, staying A_DECLARER: {}", p.getDonId(), e.getMessage());
            responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("CNTS service unavailable; will retry later")
                    .asRuntimeException());
        }
    }

    // =================================================================
    // S4-09 — DelivrerCertificat
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

            // Call CNTS for the certificate
            try {
                tn.iit.blood.donstock.soap.client.DelivrerCertificatDonResponse soapResp =
                        cntsClient.delivrerCertificatDon(
                        p.getDonId(),
                        p.getNumeroDonNational(),
                        request.getDonneurRef(),
                        p.getCentreId(),
                        LocalDate.now());

                LocalDate dateEmission = toLocalDate(soapResp.getDateEmission());
                p.setNumeroCertificat(soapResp.getNumeroCertificat());
                p.setDateCertificat(dateEmission);
                prelevementRepo.save(p);

                log.info("Certificate issued for don {}: {}", p.getDonId(), soapResp.getNumeroCertificat());

                responseObserver.onNext(DelivrerCertificatResponse.newBuilder()
                        .setNumeroCertificat(soapResp.getNumeroCertificat())
                        .setDateEmission(dateEmission.toString())
                        .setStatut("VALIDE")
                        .build());
                responseObserver.onCompleted();

            } catch (CertificatRefuseException e) {
                log.warn("CertificatRefuse for don {}: {}", p.getDonId(), e.getMessage());
                responseObserver.onError(Status.FAILED_PRECONDITION
                        .withDescription(e.getMessage()).asRuntimeException());

            } catch (Exception e) {
                log.warn("CNTS unavailable for certificate of don {}: {}", p.getDonId(), e.getMessage());
                responseObserver.onError(Status.UNAVAILABLE
                        .withDescription("CNTS service unavailable; certificate will be retried")
                        .asRuntimeException());
            }

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
        streamRegistry.register(request, responseObserver);

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

    private VerifierEtReserverResponse toReservationResponse(Reservation r) {
        return VerifierEtReserverResponse.newBuilder()
                .setStatut(parseStatutReservation(r.getStatut()))
                .setReference(r.getReference() != null ? r.getReference() : "")
                .setCentreSource(r.getCentreSource() != null ? r.getCentreSource() : "")
                .setEtaMinutes(r.getEtaMinutes() != null ? r.getEtaMinutes() : 0)
                .build();
    }

    private StatutReservation parseStatutReservation(String name) {
        try {
            return StatutReservation.valueOf(name);
        } catch (IllegalArgumentException e) {
            return StatutReservation.STATUT_RESERVATION_UNSPECIFIED;
        }
    }

    private LocalDate toLocalDate(javax.xml.datatype.XMLGregorianCalendar cal) {
        if (cal == null) return LocalDate.now();
        return LocalDate.of(cal.getYear(), cal.getMonth(), cal.getDay());
    }
}
