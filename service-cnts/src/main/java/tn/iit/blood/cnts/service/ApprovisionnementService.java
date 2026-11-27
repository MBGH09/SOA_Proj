package tn.iit.blood.cnts.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iit.blood.cnts.entity.Approvisionnement;
import tn.iit.blood.cnts.exception.StockCentralInsuffisantException;
import tn.iit.blood.cnts.repository.ApprovisionnementRepository;
import tn.iit.blood.cnts.repository.StockCentralRepository;
import tn.iit.blood.cnts.ws.generated.DemanderApprovisionnementRequest;
import tn.iit.blood.cnts.ws.generated.DemanderApprovisionnementResponse;

import java.time.LocalDate;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApprovisionnementService {

    private final ApprovisionnementRepository approRepo;
    private final StockCentralRepository stockRepo;

    @Value("${cnts.delai-normal-minutes:60}")
    private int delaiNormal;

    @Value("${cnts.delai-critique-minutes:15}")
    private int delaiCritique;

    private static final Set<String> GROUPES_VALIDES = Set.of(
            "A_POS", "A_NEG", "B_POS", "B_NEG", "AB_POS", "AB_NEG", "O_POS", "O_NEG");
    private static final Set<String> COMPOSANTS_VALIDES = Set.of("CGR", "PLASMA", "PLAQUETTES");
    private static final Set<String> URGENCES_VALIDES = Set.of("NORMALE", "HAUTE", "CRITIQUE");

    @Transactional
    public DemanderApprovisionnementResponse demander(DemanderApprovisionnementRequest req) {

        // R18 — Idempotency: same demandeId → return same reference
        return approRepo.findByDemandeId(req.getDemandeId())
                .map(this::toResponse)
                .orElseGet(() -> creerNouveau(req));
    }

    private DemanderApprovisionnementResponse creerNouveau(DemanderApprovisionnementRequest req) {

        // R10 — groupeSanguin, composant must be known values (xs:string in XSD → validated here)
        // urgence reste un enum JAXB (tns:Urgence dans le XSD)
        String groupe = req.getGroupeSanguin();
        String composant = req.getComposant();
        String urgenceStr = req.getUrgence() != null ? req.getUrgence().value() : null;

        if (groupe == null || !GROUPES_VALIDES.contains(groupe)) {
            throw new StockCentralInsuffisantException("Groupe sanguin invalide : " + groupe);
        }
        if (composant == null || !COMPOSANTS_VALIDES.contains(composant)) {
            throw new StockCentralInsuffisantException("Composant invalide : " + composant);
        }
        if (urgenceStr == null || !URGENCES_VALIDES.contains(urgenceStr)) {
            throw new StockCentralInsuffisantException("Urgence invalide : " + urgenceStr);
        }

        int quantite = req.getQuantite();
        boolean critique = "CRITIQUE".equals(urgenceStr);

        // R14 — Try CRTS of the region first, then CNTS-TUNIS
        String crtsId = "CRTS-" + req.getRegion().toUpperCase();
        String cntsId = "CNTS-TUNIS";

        String centreSource = null;
        int updated = stockRepo.decrementStock(crtsId, groupe, composant, quantite);
        if (updated > 0) {
            centreSource = crtsId;
        } else {
            // R14 — Fallback to CNTS national centre
            updated = stockRepo.decrementStock(cntsId, groupe, composant, quantite);
            if (updated > 0) {
                centreSource = cntsId;
            }
        }

        // R16 — Neither can supply
        if (centreSource == null) {
            throw new StockCentralInsuffisantException(
                    "Stock insuffisant pour " + groupe + "/" + composant + " (quantité=" + quantite + ")");
        }

        // APP-YYYY-NNNNNN from seq_approvisionnement, same pattern as DN- and CERT-.
        String annee = String.valueOf(LocalDate.now().getYear());
        long seq = approRepo.nextSequenceValue();
        String reference = String.format("APP-%s-%06d", annee, seq);

        Approvisionnement appro = new Approvisionnement();
        appro.setReference(reference);
        appro.setDemandeId(req.getDemandeId());
        appro.setHopitalId(req.getHopitalId());
        appro.setGroupeSanguin(groupe);
        appro.setComposant(composant);
        appro.setQuantiteDemandee(quantite);
        appro.setQuantiteAccordee(quantite);
        appro.setCentreSource(centreSource);
        appro.setDelaiMinutes(critique ? delaiCritique : delaiNormal);
        appro.setStatut("ACCORDEE");

        try {
            approRepo.save(appro);
            return toResponse(appro);
        } catch (DataIntegrityViolationException e) {
            // R18 — a concurrent retry inserted the same demandeId first.
            return approRepo.findByDemandeId(req.getDemandeId())
                    .map(this::toResponse)
                    .orElseThrow(() -> new StockCentralInsuffisantException(
                            "Erreur inattendue lors de l'approvisionnement"));
        }
    }

    private DemanderApprovisionnementResponse toResponse(Approvisionnement a) {
        DemanderApprovisionnementResponse resp = new DemanderApprovisionnementResponse();
        resp.setReference(a.getReference());
        resp.setQuantiteAccordee(a.getQuantiteAccordee());
        resp.setCentreSource(a.getCentreSource());
        resp.setDelaiMinutes(a.getDelaiMinutes());
        resp.setStatut(a.getStatut());
        return resp;
    }
}
