package tn.iit.blood.service_don_stock.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.iit.blood.donstock.soap.client.DemanderApprovisionnementResponse;
import tn.iit.blood.donstock.v1.StatutReservation;
import tn.iit.blood.service_don_stock.event.StockChangeEvent;
import tn.iit.blood.service_don_stock.model.Lieu;
import tn.iit.blood.service_don_stock.model.Reservation;
import tn.iit.blood.service_don_stock.model.StockNiveau;
import tn.iit.blood.service_don_stock.repository.LieuRepository;
import tn.iit.blood.service_don_stock.repository.ReservationRepository;
import tn.iit.blood.service_don_stock.repository.StockNiveauRepository;
import tn.iit.blood.service_don_stock.soap.CntsWebServiceClient;
import tn.iit.blood.service_don_stock.soap.StockInsuffisantException;

import java.util.ArrayList;
import java.util.List;

/**
 * S4-05 — Retry job for A_CONFIRMER reservations.
 *
 * Runs every RETRY_JOBS_INTERVAL_SEC (default 60 s).
 * Re-sends SOAP demanderApprovisionnement for each reservation that could not
 * reach the CNTS service when it was first created.
 * The SOAP call is idempotent via demandeId (gap 5): sending the same
 * demandeId twice returns the same reference from the CNTS service.
 */
@Component
public class RetryConfirmerJob {

    private static final Logger log = LoggerFactory.getLogger(RetryConfirmerJob.class);

    private final ReservationRepository     reservationRepo;
    private final StockNiveauRepository     stockRepo;
    private final LieuRepository            lieuRepo;
    private final CntsWebServiceClient      cntsClient;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.retry-jobs-interval-sec:60}")
    private long retryIntervalSec;

    public RetryConfirmerJob(ReservationRepository reservationRepo,
                             StockNiveauRepository stockRepo,
                             LieuRepository lieuRepo,
                             CntsWebServiceClient cntsClient,
                             ApplicationEventPublisher eventPublisher) {
        this.reservationRepo = reservationRepo;
        this.stockRepo        = stockRepo;
        this.lieuRepo         = lieuRepo;
        this.cntsClient       = cntsClient;
        this.eventPublisher   = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${app.retry-jobs-interval-sec:60}000")
    @Transactional
    public void run() {
        List<Reservation> pending = reservationRepo.findByStatut("A_CONFIRMER");
        if (pending.isEmpty()) return;

        log.info("RetryConfirmerJob: retrying {} A_CONFIRMER reservation(s)", pending.size());

        List<StockNiveau> changed = new ArrayList<>();

        for (Reservation r : pending) {
            Lieu lieu = lieuRepo.findById(r.getLieuId()).orElse(null);
            if (lieu == null) {
                log.warn("Lieu {} not found for reservation {}, skipping", r.getLieuId(), r.getDemandeId());
                continue;
            }

            try {
                DemanderApprovisionnementResponse soapResp = cntsClient.demanderApprovisionnement(
                        lieu.getId(),
                        lieu.getRegion(),
                        r.getGroupeSanguin(),
                        r.getComposant(),
                        r.getQuantite(),
                        "NORMALE",   // urgence not stored on the reservation; use NORMALE for retries
                        r.getDemandeId());

                r.setStatut(StatutReservation.APPROVISIONNEMENT_COMMANDE.name());
                r.setReference(soapResp.getReference());
                r.setCentreSource(soapResp.getCentreSource());
                r.setEtaMinutes(soapResp.getDelaiMinutes());
                reservationRepo.save(r);

                // Increase en_transit at the hospital place
                stockRepo.findByKeyForUpdate(lieu.getId(), r.getGroupeSanguin(), r.getComposant())
                        .ifPresent(stock -> {
                            stock.setEnTransit(stock.getEnTransit() + r.getQuantite());
                            stockRepo.save(stock);
                            changed.add(stock);
                        });

                log.info("RetryConfirmerJob: demande {} confirmed → ref={} source={}",
                        r.getDemandeId(), soapResp.getReference(), soapResp.getCentreSource());

            } catch (StockInsuffisantException e) {
                r.setStatut(StatutReservation.STOCK_INSUFFISANT_PARTOUT.name());
                reservationRepo.save(r);
                log.warn("RetryConfirmerJob: stock insuffisant for demande {}", r.getDemandeId());

            } catch (Exception e) {
                // Still unavailable — will retry next cycle
                log.warn("RetryConfirmerJob: CNTS still unavailable for demande {}: {}",
                        r.getDemandeId(), e.getMessage());
            }
        }

        if (!changed.isEmpty()) {
            eventPublisher.publishEvent(new StockChangeEvent(this, changed));
        }
    }
}
