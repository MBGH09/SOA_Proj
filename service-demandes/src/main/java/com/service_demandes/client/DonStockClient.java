package com.service_demandes.client;

/**
 * Port vers service-don-stock (appel 1 du projet : gRPC VerifierEtReserver).
 * Le service metier ne connait que cette interface. L'implementation gRPC (deadline GRPC_DEADLINE_MS,
 * disjoncteur donStock) arrive en S6-09.
 */
public interface DonStockClient {

    /**
     * @throws DonStockIndisponibleException si le service est injoignable, trop lent ou le disjoncteur est ouvert
     */
    ReservationResultat verifierEtReserver(ReservationCommande commande);
}
