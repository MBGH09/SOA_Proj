package tn.iit.blood.cnts.endpoint;

import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import tn.iit.blood.cnts.service.CertificatService;
import tn.iit.blood.cnts.service.PrelevementService;
import tn.iit.blood.cnts.ws.generated.DelivrerCertificatDonRequest;
import tn.iit.blood.cnts.ws.generated.DelivrerCertificatDonResponse;
import tn.iit.blood.cnts.ws.generated.EnregistrerPrelevementRequest;
import tn.iit.blood.cnts.ws.generated.EnregistrerPrelevementResponse;

/**
 * SOAP endpoint — S5-05.
 * Exposes: enregistrerPrelevement + delivrerCertificatDon.
 * demanderApprovisionnement is added in S5-06.
 */
@Endpoint
@RequiredArgsConstructor
public class CntsEndpoint {

    private static final String NAMESPACE = "http://iit.tn/soa/cnts";

    private final PrelevementService prelevementService;
    private final CertificatService  certificatService;

    /**
     * Operation 1 — enregistrerPrelevement (S5-05)
     * Declares a blood collection to CNTS.
     * Idempotent by donIdLocal (R18). Returns QUALIFICATION_EN_COURS.
     */
    @PayloadRoot(namespace = NAMESPACE, localPart = "enregistrerPrelevementRequest")
    @ResponsePayload
    public EnregistrerPrelevementResponse enregistrerPrelevement(
            @RequestPayload EnregistrerPrelevementRequest request) {
        return prelevementService.enregistrer(request);
    }

    /**
     * Operation 3 — delivrerCertificatDon (S5-05)
     * Issues the official donation certificate.
     * Idempotent by donIdLocal (R25). No health data stored (R27).
     */
    @PayloadRoot(namespace = NAMESPACE, localPart = "delivrerCertificatDonRequest")
    @ResponsePayload
    public DelivrerCertificatDonResponse delivrerCertificatDon(
            @RequestPayload DelivrerCertificatDonRequest request) {
        return certificatService.delivrer(request);
    }
}
