package com.service_demandes.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation de secours : active tant que le vrai client gRPC n'est pas disponible
 * et qu'on n'est pas en profil {@code mock}.
 * Chaque appel lève {@link DonStockIndisponibleException} → la demande reste {@code EN_ATTENTE}.
 */
@Component
@Profile("!mock")
@ConditionalOnMissingBean(DonStockGrpcClient.class)
public class DonStockClientNonConfigure implements DonStockClient {

    private static final Logger log = LoggerFactory.getLogger(DonStockClientNonConfigure.class);

    @Override
    public ReservationResultat verifierEtReserver(ReservationCommande commande) {
        log.warn("Client gRPC Don & Stock non configuré — demande {} laissée EN_ATTENTE", commande.demandeId());
        throw new DonStockIndisponibleException("Client gRPC Don & Stock non configuré");
    }

    @Override
    public void libererReservation(UUID demandeId, String reference) {
        log.warn("Libération impossible — client gRPC non configuré (demande={})", demandeId);
    }

    @Override
    public Optional<ConsultationResultat> consulterReservation(UUID demandeId) {
        throw new DonStockIndisponibleException("Client gRPC Don & Stock non configuré");
    }
}
