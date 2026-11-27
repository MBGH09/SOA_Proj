package com.service_demandes.repository;

import com.service_demandes.entity.AppelDon;
import com.service_demandes.entity.Composant;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.StatutAppelDon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppelDonRepository extends JpaRepository<AppelDon, UUID> {

    /** Toutes les pénuries ouvertes (utilisé par le job de réconciliation pour les fermer). */
    List<AppelDon> findByStatut(StatutAppelDon statut);

    /** Filtre liste publique par statut. */
    List<AppelDon> findByStatutOrderByDateOuvertureDesc(StatutAppelDon statut);

    /**
     * S6-07 : réutiliser un appel déjà ouvert pour ce groupe + composant pour éviter les doublons.
     * Un seul appel OUVERT par combinaison groupe / composant est suffisant.
     */
    Optional<AppelDon> findFirstByGroupeSanguinAndComposantAndStatut(
            GroupeSanguin groupeSanguin, Composant composant, StatutAppelDon statut);
}
