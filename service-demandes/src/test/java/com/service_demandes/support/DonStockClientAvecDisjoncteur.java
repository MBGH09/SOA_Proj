package com.service_demandes.support;

import com.service_demandes.client.ConsultationResultat;
import com.service_demandes.client.DonStockClient;
import com.service_demandes.client.DonStockIndisponibleException;
import com.service_demandes.client.ReservationCommande;
import com.service_demandes.client.ReservationResultat;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Implémentation de test de {@link DonStockClient} instrumentée avec le vrai
 * disjoncteur Resilience4j {@code donStock}.
 *
 * <p>Elle remplace {@code DonStockClientNonConfigure} en profil {@code test}
 * grâce à {@code @Primary}. Chaque test configure son comportement via
 * {@link #configurerComportement(Supplier)} avant d'appeler le service.</p>
 *
 * <p>Utilisation typique :</p>
 * <pre>
 * // Toujours échouer
 * clientInstrumente.configurerComportement(() -> {
 *     throw new DonStockIndisponibleException("simulé");
 * });
 *
 * // Réussir
 * clientInstrumente.configurerComportement(() ->
 *     new ReservationResultat(StatutReservation.RESERVEE_STOCK_LOCAL, "RES-1", null, null));
 * </pre>
 */
@Component
@Primary
@Profile("test")
public class DonStockClientAvecDisjoncteur implements DonStockClient {

    private final AtomicReference<Supplier<ReservationResultat>> comportementVerification =
            new AtomicReference<>(() -> { throw new DonStockIndisponibleException("comportement non configuré"); });

    private final AtomicReference<Supplier<Optional<ConsultationResultat>>> comportementConsultation =
            new AtomicReference<>(Optional::empty);

    // -------------------------------------------------------------------------
    // Configuration du comportement (à appeler au début de chaque test)
    // -------------------------------------------------------------------------

    public void configurerComportement(Supplier<ReservationResultat> comportement) {
        this.comportementVerification.set(comportement);
    }

    public void configurerConsultation(Supplier<Optional<ConsultationResultat>> comportement) {
        this.comportementConsultation.set(comportement);
    }

    // -------------------------------------------------------------------------
    // VerifierEtReserver — protégé par le vrai disjoncteur donStock
    // -------------------------------------------------------------------------

    @Override
    @CircuitBreaker(name = "donStock", fallbackMethod = "quandDisjoncteurOuvertPourVerification")
    public ReservationResultat verifierEtReserver(ReservationCommande commande) {
        return comportementVerification.get().get();
    }

    @SuppressWarnings("unused")
    private ReservationResultat quandDisjoncteurOuvertPourVerification(
            ReservationCommande commande, CallNotPermittedException erreurDisjoncteur) {
        throw new DonStockIndisponibleException("Disjoncteur ouvert (test)", erreurDisjoncteur);
    }

    // -------------------------------------------------------------------------
    // ConsulterReservation — protégé par le vrai disjoncteur donStock
    // -------------------------------------------------------------------------

    @Override
    @CircuitBreaker(name = "donStock", fallbackMethod = "quandDisjoncteurOuvertPourConsultation")
    public Optional<ConsultationResultat> consulterReservation(UUID demandeId) {
        return comportementConsultation.get().get();
    }

    @SuppressWarnings("unused")
    private Optional<ConsultationResultat> quandDisjoncteurOuvertPourConsultation(
            UUID demandeId, CallNotPermittedException erreurDisjoncteur) {
        throw new DonStockIndisponibleException("Disjoncteur ouvert (test)", erreurDisjoncteur);
    }

    // -------------------------------------------------------------------------
    // LibererReservation — pas de disjoncteur en test
    // -------------------------------------------------------------------------

    @Override
    public void libererReservation(UUID demandeId, String reference) {
        // Best-effort en test : aucune action nécessaire
    }
}
