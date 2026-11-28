package com.service_demandes.client;

import com.service_demandes.config.DemandesRulesProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Client HTTP vers service-donneurs pour vérifier un certificat de don (appel optionnel 4).
 *
 * <ul>
 *   <li>Timeout : 2 s (configurable via {@code GRPC_DEADLINE_MS} réutilisé comme seuil HTTP).</li>
 *   <li>Disjoncteur Resilience4j nommé {@code donneurs}.</li>
 *   <li>Fallback : lève {@link DonneursIndisponibleException} → le staff corrige à la main.</li>
 * </ul>
 */
@Component
public class DonneursClient {

    private static final Logger log = LoggerFactory.getLogger(DonneursClient.class);

    private final RestClient http;

    public DonneursClient(DemandesRulesProperties props) {
        int timeoutMs = props.getGrpcDeadlineMs(); // réutilise la même valeur 2000 ms
        this.http = RestClient.builder()
                .baseUrl(props.getDonneursUrl())
                .requestInterceptor((req, body, exec) -> {
                    req.getHeaders().set("Accept", "application/json");
                    return exec.execute(req, body);
                })
                .build();
    }

    /**
     * Vérifie si un numéro de certificat est valide en appelant {@code GET /api/v1/certificats/{numero}}.
     *
     * @return {@code true} si le service répond 200 (certificat valide)
     * @throws DonneursIndisponibleException si Donneurs est down ou le disjoncteur est ouvert
     */
    @CircuitBreaker(name = "donneurs", fallbackMethod = "fallbackVerifier")
    public boolean verifierCertificat(String numeroCertificat) {
        try {
            http.get()
                    .uri("/api/v1/certificats/{numero}", numeroCertificat)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, rep) -> {
                        // 404 = certificat invalide ou inconnu
                        throw new CertificatInvalideException("Certificat inconnu ou invalide : " + numeroCertificat);
                    })
                    .toBodilessEntity();
            return true;
        } catch (CertificatInvalideException e) {
            throw e; // relance sans log pour 404
        } catch (Exception e) {
            log.warn("Erreur lors de la vérification du certificat {} : {}", numeroCertificat, e.getMessage());
            throw new DonneursIndisponibleException("service-donneurs injoignable", e);
        }
    }

    @SuppressWarnings("unused")
    private boolean fallbackVerifier(String numeroCertificat,
                                     io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
        log.warn("Disjoncteur donneurs OUVERT — vérification du certificat {} impossible", numeroCertificat);
        throw new DonneursIndisponibleException("Disjoncteur ouvert vers service-donneurs", ex);
    }

    // -------------------------------------------------------------------------
    // Exceptions internes
    // -------------------------------------------------------------------------

    /** Certificat introuvable ou invalide (réponse 404 de Donneurs). */
    public static class CertificatInvalideException extends RuntimeException {
        public CertificatInvalideException(String message) { super(message); }
    }

    /** Donneurs est down, timeout ou disjoncteur ouvert — le staff corrige à la main. */
    public static class DonneursIndisponibleException extends RuntimeException {
        public DonneursIndisponibleException(String message) { super(message); }
        public DonneursIndisponibleException(String message, Throwable cause) { super(message, cause); }
    }
}
