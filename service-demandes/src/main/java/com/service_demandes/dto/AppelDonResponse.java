package com.service_demandes.dto;


import java.time.LocalDateTime;
import java.util.UUID;

public record AppelDonResponse(

        UUID id,

        UUID demandeId,

        String groupeSanguin,

        String composant,

        String statut,

        LocalDateTime dateCreation
) {}