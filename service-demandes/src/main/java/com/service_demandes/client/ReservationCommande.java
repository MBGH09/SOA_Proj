package com.service_demandes.client;

import com.service_demandes.entity.Composant;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.Urgence;

import java.util.UUID;

/** Ce que Demandes envoie a Don & Stock (ReservationRequest). demandeId sert de cle d'idempotence (R18). */
public record ReservationCommande(
        UUID demandeId,
        String lieuId,
        GroupeSanguin groupeSanguin,
        Composant composant,
        int quantite,
        Urgence urgence
) {
}
