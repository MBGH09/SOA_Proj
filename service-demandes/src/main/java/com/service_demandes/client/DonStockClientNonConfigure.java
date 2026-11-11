package com.service_demandes.client;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Implementation par defaut tant que le client gRPC (S6-09) n'existe pas : Don & Stock est considere
 * comme indisponible, donc toute demande reste EN_ATTENTE (repli de l'etude). Elle disparaitra quand
 * le vrai client sera ajoute.
 */
@Component
@Profile("!mock")
public class DonStockClientNonConfigure implements DonStockClient {

    @Override
    public ReservationResultat verifierEtReserver(ReservationCommande commande) {
        throw new DonStockIndisponibleException("Client gRPC de Don & Stock non configuré (S6-09)");
    }
}
