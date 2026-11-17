package tn.iit.blood.cnts.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iit.blood.cnts.entity.Certificat;
import tn.iit.blood.cnts.entity.PrelevementDeclare;
import tn.iit.blood.cnts.exception.CertificatRefuseException;
import tn.iit.blood.cnts.repository.CertificatRepository;
import tn.iit.blood.cnts.repository.PrelevementDeclareRepository;
import tn.iit.blood.cnts.ws.generated.DelivrerCertificatDonRequest;
import tn.iit.blood.cnts.ws.generated.DelivrerCertificatDonResponse;

import javax.xml.datatype.DatatypeFactory;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.GregorianCalendar;

@Service
@RequiredArgsConstructor
public class CertificatService {

    private final CertificatRepository certificatRepo;
    private final PrelevementDeclareRepository prelevementRepo;

    @Transactional
    public DelivrerCertificatDonResponse delivrer(DelivrerCertificatDonRequest req) {

        // R25 — Idempotency: same donIdLocal → return same certificate
        return certificatRepo.findByDonIdLocal(req.getDonIdLocal())
                .map(this::toResponse)
                .orElseGet(() -> creerNouveau(req));
    }

    private DelivrerCertificatDonResponse creerNouveau(DelivrerCertificatDonRequest req) {

        // Verify the prelevement was declared (must exist with numeroDonNational)
        PrelevementDeclare p = prelevementRepo
                .findByNumeroDonNational(req.getNumeroDonNational())
                .orElseThrow(() -> new CertificatRefuseException(
                    "Don inconnu : " + req.getNumeroDonNational()));

        // Cross-check: donIdLocal must match
        if (!p.getDonIdLocal().equals(req.getDonIdLocal())) {
            throw new CertificatRefuseException(
                "Le donIdLocal ne correspond pas au numéro de don national");
        }

        // Generate CERT-YYYY-NNNNNN using PostgreSQL sequence seq_certificat (V1__init.sql)
        String annee = String.valueOf(LocalDate.now().getYear());
        long seq = certificatRepo.nextSequenceValue();
        String numeroCertificat = String.format("CERT-%s-%06d", annee, seq);

        LocalDate dateDon = req.getDateDon().toGregorianCalendar().toZonedDateTime().toLocalDate();

        Certificat cert = new Certificat();
        cert.setNumeroCertificat(numeroCertificat);
        cert.setNumeroDonNational(req.getNumeroDonNational());
        cert.setDonIdLocal(req.getDonIdLocal());
        cert.setCentreId(req.getCentreId());
        cert.setDateDon(dateDon);
        cert.setDateEmission(LocalDate.now());
        cert.setStatut("VALIDE");

        // R27 — donneurRef is accepted but NOT stored (no health or personal data)
        certificatRepo.save(cert);
        return toResponse(cert);
    }

    private DelivrerCertificatDonResponse toResponse(Certificat cert) {
        try {
            DelivrerCertificatDonResponse resp = new DelivrerCertificatDonResponse();
            resp.setNumeroCertificat(cert.getNumeroCertificat());
            resp.setStatut(cert.getStatut());

            GregorianCalendar cal = GregorianCalendar.from(
                cert.getDateEmission().atStartOfDay(ZoneId.systemDefault()));
            resp.setDateEmission(DatatypeFactory.newInstance().newXMLGregorianCalendar(cal));
            return resp;
        } catch (Exception e) {
            throw new RuntimeException("Erreur conversion date", e);
        }
    }
}
