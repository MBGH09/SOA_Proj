package com.service_demandes.client;

import java.util.UUID;

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

    /** Libere une reservation locale devenue inutile, notamment apres l'annulation de la demande. */
    void libererReservation(UUID demandeId, String reference);
}
