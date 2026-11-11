package com.service_demandes.controller;

import com.service_demandes.dto.CreateDemandeRequest;
import com.service_demandes.dto.DemandeResponse;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.service.DemandeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demandes")
public class DemandeController {

    private final DemandeService service;

    public DemandeController(DemandeService service) {
        this.service = service;
    }

    /**
     * 201 si le sang est reserve tout de suite (SATISFAITE), 202 dans tous les autres cas
     * (approvisionnement, penurie, Don & Stock indisponible). 400 est gere par GlobalExceptionHandler.
     */
    @PostMapping
    public ResponseEntity<DemandeResponse> creer(@Valid @RequestBody CreateDemandeRequest requete) {
        Demande demande = service.creer(requete);
        HttpStatus status = demande.getStatut() == StatutDemande.SATISFAITE ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(DemandeResponse.from(demande));
    }
}
