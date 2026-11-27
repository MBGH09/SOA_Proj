package com.service_demandes.controller;

import com.service_demandes.dto.CompensationResponse;
import com.service_demandes.dto.CreateCompensationRequest;
import com.service_demandes.entity.Compensation;
import com.service_demandes.service.CompensationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * S6-08 — Compensations familiales sur une demande.
 *
 * <table border="1">
 *   <tr><th>Méthode</th><th>Chemin</th><th>Codes</th><th>Description</th></tr>
 *   <tr><td>POST</td><td>/api/v1/demandes/{id}/compensations</td>
 *       <td>201, 400, 404, 409, 503</td>
 *       <td>Enregistre un donneur famille par son numéro de certificat</td></tr>
 *   <tr><td>GET</td><td>/api/v1/demandes/{id}/compensations</td>
 *       <td>200, 404</td>
 *       <td>Liste les compensations d'une demande</td></tr>
 * </table>
 *
 * <p>Codes de retour :</p>
 * <ul>
 *   <li>201 — enregistrement réussi</li>
 *   <li>400 — certificat invalide / demande sans compensation demandée</li>
 *   <li>404 — demande inconnue</li>
 *   <li>409 — numéro de certificat déjà utilisé (R26)</li>
 *   <li>503 — service-donneurs injoignable → corriger à la main via PATCH /demandes/{id}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/demandes/{demandeId}/compensations")
public class CompensationController {

    private final CompensationService service;

    public CompensationController(CompensationService service) {
        this.service = service;
    }

    /**
     * Enregistre un donneur de compensation.
     * Le numéro est vérifié dans service-donneurs (appel 4 optionnel) avant d'être compté.
     */
    @PostMapping
    public ResponseEntity<CompensationResponse> enregistrer(
            @PathVariable UUID demandeId,
            @Valid @RequestBody CreateCompensationRequest requete) {

        Compensation compensation = service.enregistrer(demandeId, requete);
        // compensationRecue est déjà calculé et mis à jour dans la demande par le service.
        // On le passe explicitement au DTO plutôt que de traverser le proxy lazy.
        int compensationRecue = (int) service.compterCompensations(demandeId);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(compensation.getId())
                .toUri();

        return ResponseEntity.created(location)
                .body(CompensationResponse.from(compensation, compensationRecue));
    }

    /**
     * Liste toutes les compensations enregistrées pour une demande.
     * Utile pour le suivi côté hôpital (D7).
     */
    @GetMapping
    public List<CompensationResponse> lister(@PathVariable UUID demandeId) {
        List<Compensation> liste = service.lister(demandeId);
        // compensationRecue = taille de la liste pour chaque élément (cohérence vue)
        int total = liste.size();
        return liste.stream()
                .map(c -> CompensationResponse.from(c, total))
                .toList();
    }
}
