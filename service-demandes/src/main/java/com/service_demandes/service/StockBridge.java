package com.service_demandes.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Interface commune aux deux implémentations du pont stock (réelle et mock).
 * Le contrôleur {@link com.service_demandes.controller.StockController} dépend uniquement de cette interface.
 */
public interface StockBridge {

    /**
     * Crée un {@link SseEmitter} pour un client HTTP.
     * Envoie le snapshot du cache en premier, puis les changements en temps réel.
     *
     * @param lieuId filtre optionnel (null = tout)
     */
    SseEmitter souscrire(String lieuId);

    /**
     * Retourne le contenu actuel du cache de niveaux de stock.
     *
     * @param lieuId    filtre optionnel (null = tout)
     * @param groupe    filtre optionnel sur le groupe sanguin (null = tout)
     * @throws com.service_demandes.exception.ServiceIndisponibleException si cache vide et stream down
     */
    List<Map<String, Object>> obtenirNiveaux(String lieuId, String groupe);

    /** Indique si le bridge est opérationnel (stream connecté ou cache non vide). */
    boolean estConnecte();
}
