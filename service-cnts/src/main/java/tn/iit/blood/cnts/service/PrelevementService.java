package tn.iit.blood.cnts.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iit.blood.cnts.entity.PrelevementDeclare;
import tn.iit.blood.cnts.exception.PrelevementInvalideException;
import tn.iit.blood.cnts.repository.PrelevementDeclareRepository;
import tn.iit.blood.cnts.ws.generated.EnregistrerPrelevementRequest;
import tn.iit.blood.cnts.ws.generated.EnregistrerPrelevementResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PrelevementService {

    private final PrelevementDeclareRepository prelevementRepo;

    @Value("${cnts.volume-prelevement-ml:450}")
    private int volumeAttenu;

    /** Valeurs autorisées pour le groupe sanguin (R10). */
    private static final Set<String> GROUPES_VALIDES = Set.of(
            "A_POS", "A_NEG", "B_POS", "B_NEG", "AB_POS", "AB_NEG", "O_POS", "O_NEG");

    @Transactional
    public EnregistrerPrelevementResponse enregistrer(EnregistrerPrelevementRequest req) {

        // R18 — Idempotency: same donIdLocal → return same result
        return prelevementRepo.findByDonIdLocal(req.getDonIdLocal())
                .map(this::toResponse)
                .orElseGet(() -> creerNouveau(req));
    }

    private EnregistrerPrelevementResponse creerNouveau(EnregistrerPrelevementRequest req) {

        // R8 — Volume must be exactly 450 ml
        if (req.getVolumeMl() != volumeAttenu) {
            throw new PrelevementInvalideException(
                    "Volume invalide : attendu " + volumeAttenu + " ml, reçu " + req.getVolumeMl() + " ml");
        }

        // R10 — groupeSanguin is a JAXB enum (tns:GroupeSanguin in XSD).
        // .value() converts it to String. null-check guards against unmarshalling edge cases.
        if (req.getGroupeSanguin() == null) {
            throw new PrelevementInvalideException("Groupe sanguin inconnu ou invalide");
        // R10 — Blood group must be a known value.
        // groupeSanguin est maintenant xs:string dans le XSD (pas l'enum JAXB) pour
        // que les valeurs inconnues atteignent ce service et remontent via
        // PrelevementInvalideFault au lieu d'une generic SOAP client-fault.
        String groupe = req.getGroupeSanguin();
        if (groupe == null || !GROUPES_VALIDES.contains(groupe)) {
            throw new PrelevementInvalideException(
                    "Groupe sanguin inconnu ou invalide : " + groupe);
        }
        String groupe = req.getGroupeSanguin().value();

        // Date must not be in the future
        LocalDate datePrelev = req.getDatePrelevement().toGregorianCalendar().toZonedDateTime()
                .toLocalDate();
        if (datePrelev.isAfter(LocalDate.now())) {
            throw new PrelevementInvalideException("La date de prélèvement ne peut pas être dans le futur");
        }

        // Generate DN-YYYY-NNNNNN using PostgreSQL sequence seq_don_national (V1__init.sql)
        String annee = String.valueOf(LocalDate.now().getYear());
        long seq = prelevementRepo.nextSequenceValue();
        String numeroDonNational = String.format("DN-%s-%06d", annee, seq);

        PrelevementDeclare p = new PrelevementDeclare();
        p.setNumeroDonNational(numeroDonNational);
        p.setDonIdLocal(req.getDonIdLocal());
        p.setGroupeSanguin(groupe);
        p.setGroupeSanguin(groupe);  // String direct — plus de .value()
        p.setVolumeMl(req.getVolumeMl());
        p.setCentreId(req.getCentreId());
        p.setDatePrelevement(datePrelev);
        p.setStatut("QUALIFICATION_EN_COURS");

        try {
            prelevementRepo.save(p);
            return toResponse(p);
        } catch (DataIntegrityViolationException e) {
            // R18 — Concurrent duplicate insert: reload the existing record
            return prelevementRepo.findByDonIdLocal(req.getDonIdLocal())
                    .map(this::toResponse)
                    .orElseThrow(() -> new PrelevementInvalideException("Erreur inattendue lors de l'enregistrement"));
        }
    }

    private EnregistrerPrelevementResponse toResponse(PrelevementDeclare p) {
        EnregistrerPrelevementResponse resp = new EnregistrerPrelevementResponse();
        resp.setNumeroDonNational(p.getNumeroDonNational());
        resp.setStatut(p.getStatut());
        resp.getComposantsPrevus().addAll(List.of("CGR", "PLASMA", "PLAQUETTES"));
        return resp;
    }
}
