package com.service_demandes.controller;

import com.service_demandes.dto.CreateDemandeRequest;
import com.service_demandes.dto.DemandeResponse;
import com.service_demandes.dto.ModifierDemandeRequest;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.service.DemandeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints REST pour les demandes de sang des hôpitaux.
 *
 * <table border="1">
 *   <tr><th>Méthode</th><th>Chemin</th><th>Codes</th><th>Description</th></tr>
 *   <tr><td>POST</td>  <td>/api/v1/demandes</td>       <td>201, 202, 400</td><td>Crée une demande et tente la réservation</td></tr>
 *   <tr><td>GET</td>   <td>/api/v1/demandes</td>       <td>200</td>          <td>Liste avec filtres optionnels</td></tr>
 *   <tr><td>GET</td>   <td>/api/v1/demandes/{id}</td>  <td>200, 404</td>     <td>Lit une demande</td></tr>
 *   <tr><td>PATCH</td> <td>/api/v1/demandes/{id}</td>  <td>200, 400, 404, 409</td><td>Annule ou corrige compensationRecue</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/demandes")
public class DemandeController {

    private final DemandeService demandeService;

    public DemandeController(DemandeService demandeService) {
        this.demandeService = demandeService;
    }

    /**
     * Crée une demande et tente immédiatement la réservation dans service-don-stock.
     * <ul>
     *   <li>201 si le sang est réservé localement (SATISFAITE).</li>
     *   <li>202 si la demande est en attente d'approvisionnement, en pénurie,
     *       ou si Don & Stock est temporairement injoignable.</li>
     *   <li>400 si les données sont invalides (géré par {@code GlobalExceptionHandler}).</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<DemandeResponse> creer(@Valid @RequestBody CreateDemandeRequest corps) {
        Demande demandeCreee = demandeService.creer(corps);
        HttpStatus codeHttp = choisirCodeHttpCreation(demandeCreee.getStatut());
        return ResponseEntity.status(codeHttp).body(DemandeResponse.from(demandeCreee));
    }

    private HttpStatus choisirCodeHttpCreation(StatutDemande statut) {
        return statut == StatutDemande.SATISFAITE ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
    }

    /**
     * Liste les demandes. Les deux filtres sont optionnels et combinables.
     *
     * @param filtreStatut    filtre par statut (ex: {@code EN_ATTENTE}, {@code SATISFAITE})
     * @param filtreHopitalId filtre par identifiant d'hôpital
     */
    @GetMapping
    public List<DemandeResponse> lister(
            @RequestParam(required = false) StatutDemande filtreStatut,
            @RequestParam(name = "hopitalId", required = false) String filtreHopitalId) {
        return demandeService.lister(filtreStatut, filtreHopitalId)
                .stream()
                .map(DemandeResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public DemandeResponse lire(@PathVariable UUID id) {
        return DemandeResponse.from(demandeService.lire(id));
    }

    /**
     * Annule une demande ouverte ({@code statut = ANNULEE})
     * et/ou corrige le compteur {@code compensationRecue} à la main
     * (repli quand service-donneurs est injoignable).
     */
    @PatchMapping("/{id}")
    public DemandeResponse modifier(
            @PathVariable UUID id,
            @Valid @RequestBody ModifierDemandeRequest corps) {
        return DemandeResponse.from(demandeService.modifier(id, corps));
    }
}
