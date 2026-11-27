package com.service_demandes.client;

import java.util.Optional;
import java.util.UUID;

/**
 * Port vers service-don-stock.
 * <ul>
 *   <li>Appel 1 du projet : gRPC {@code VerifierEtReserver} (S6-05)</li>
 *   <li>Consultation réservation : gRPC {@code ConsulterReservation} (S6-10)</li>
 *   <li>Stream stock : gRPC {@code SuivreNiveauxStock} (S6-11)</li>
 * </ul>
 */
public interface DonStockClient {

    /**
     * Vérifie et réserve du sang pour une demande.
     *
     * @throws DonStockIndisponibleException si le service est injoignable, trop lent ou le disjoncteur est ouvert
     */
    ReservationResultat verifierEtReserver(ReservationCommande commande);

    /**
     * Libère une réservation locale devenue inutile (demande annulée pendant l'appel gRPC).
     *
     * @throws DonStockIndisponibleException si le service ne répond pas
     */
    void libererReservation(UUID demandeId, String reference);

    /**
     * Consulte l'état actuel d'une réservation par son identifiant de demande.
     * Retourne {@code Optional.empty()} si la réservation est introuvable (NOT_FOUND).
     *
     * @throws DonStockIndisponibleException si le service est injoignable ou le disjoncteur est ouvert
     */
    Optional<ConsultationResultat> consulterReservation(UUID demandeId);
}
