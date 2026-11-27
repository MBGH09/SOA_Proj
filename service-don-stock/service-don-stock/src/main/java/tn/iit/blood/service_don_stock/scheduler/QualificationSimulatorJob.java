package tn.iit.blood.service_don_stock.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.iit.blood.service_don_stock.event.StockChangeEvent;
import tn.iit.blood.service_don_stock.model.Prelevement;
import tn.iit.blood.service_don_stock.model.StockNiveau;
import tn.iit.blood.service_don_stock.repository.PrelevementRepository;
import tn.iit.blood.service_don_stock.repository.StockNiveauRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * S4-08 – Qualification simulator.
 * After DELAI_QUALIFICATION_SEC, moves pouches from en_qualification → disponible
 * and sets the prelevement status to QUALIFIE.
 */
@Component
public class QualificationSimulatorJob {

    private static final Logger log = LoggerFactory.getLogger(QualificationSimulatorJob.class);

    @Value("${app.delai-qualification-sec:20}")
    private long delaiQualificationSec;

    private final PrelevementRepository  prelevementRepo;
    private final StockNiveauRepository  stockRepo;
    private final ApplicationEventPublisher eventPublisher;

    private static final String[] COMPOSANTS = {"CGR", "PLASMA", "PLAQUETTES"};

    public QualificationSimulatorJob(PrelevementRepository prelevementRepo,
                                     StockNiveauRepository stockRepo,
                                     ApplicationEventPublisher eventPublisher) {
        this.prelevementRepo = prelevementRepo;
        this.stockRepo        = stockRepo;
        this.eventPublisher   = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${app.delai-qualification-sec:20}000")
    @Transactional
    public void run() {
        Instant cutoff = Instant.now().minusSeconds(delaiQualificationSec);
        List<Prelevement> ready = prelevementRepo
            .findByStatutAndCreatedAtBefore("QUALIFICATION_EN_COURS", cutoff);

        if (ready.isEmpty()) return;
        log.info("Qualification simulator: qualifying {} prelèvements", ready.size());

        List<StockNiveau> changed = new ArrayList<>();
        for (Prelevement p : ready) {
            for (String composant : COMPOSANTS) {
                stockRepo.findByKeyForUpdate(p.getCentreId(), p.getGroupeSanguin(), composant)
                    .ifPresent(stock -> {
                        if (stock.getEnQualification() > 0) {
                            stock.setEnQualification(stock.getEnQualification() - 1);
                            stock.setDisponible(stock.getDisponible() + 1);
                            stockRepo.save(stock);
                            changed.add(stock);
                        }
                    });
            }
            p.setStatut("QUALIFIE");
            prelevementRepo.save(p);
            log.info("Prelevement {} qualified (don {})", p.getId(), p.getDonId());
        }

        if (!changed.isEmpty()) {
            eventPublisher.publishEvent(new StockChangeEvent(this, changed));
        }
    }
}
