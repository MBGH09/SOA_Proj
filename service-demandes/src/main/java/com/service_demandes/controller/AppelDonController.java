package com.service_demandes.controller;

import com.service_demandes.dto.AppelDonResponse;
import com.service_demandes.dto.CreateAppelDonRequest;
import com.service_demandes.entity.StatutAppelDon;
import com.service_demandes.service.AppelDonService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * S6-07 — Appels publics aux dons.
 *
 * <table border="1">
 *   <tr><th>Méthode</th><th>Chemin</th><th>Codes</th><th>Accès</th></tr>
 *   <tr><td>POST</td>  <td>/api/v1/appels</td>              <td>201</td>     <td>HOPITAL, ADMIN</td></tr>
 *   <tr><td>GET</td>   <td>/api/v1/appels</td>              <td>200</td>     <td>public</td></tr>
 *   <tr><td>GET</td>   <td>/api/v1/appels/{id}</td>         <td>200, 404</td><td>public</td></tr>
 *   <tr><td>PATCH</td> <td>/api/v1/appels/{id}</td>         <td>200, 404</td><td>HOPITAL, ADMIN</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/appels")
public class AppelDonController {

    private final AppelDonService service;

    public AppelDonController(AppelDonService service) {
        this.service = service;
    }

    /**
     * Ouvre un appel manuellement.
     * Les appels créés automatiquement sur pénurie passent par {@link AppelDonService#ouvrirOuReutiliser}.
     */
    @PostMapping
    public ResponseEntity<AppelDonResponse> ouvrir(@Valid @RequestBody CreateAppelDonRequest requete) {
        var appel = service.ouvrir(requete);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(appel.getId())
                .toUri();
        return ResponseEntity.created(location).body(AppelDonResponse.from(appel));
    }

    /**
     * Liste les appels. Le filtre {@code ?statut=OUVERT} est le cas d'usage principal
     * (affichage public pour les donneurs).
     */
    @GetMapping
    public List<AppelDonResponse> lister(@RequestParam(required = false) StatutAppelDon statut) {
        return service.lister(statut).stream().map(AppelDonResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AppelDonResponse lire(@PathVariable UUID id) {
        return AppelDonResponse.from(service.lire(id));
    }

    /**
     * Ferme un appel (statut → FERME).
     * Idempotent : fermer un appel déjà fermé retourne 200.
     */
    @PatchMapping("/{id}")
    public AppelDonResponse fermer(@PathVariable UUID id) {
        return AppelDonResponse.from(service.fermer(id));
    }
}
