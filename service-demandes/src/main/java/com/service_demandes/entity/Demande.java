package com.service_demandes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Demande de sang d'un hopital. Le patient n'est identifie que par une reference anonyme (R19).
 */
@Entity
@Table(name = "demande")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Demande {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String hopitalId;

    @Column(nullable = false)
    private String hopitalNom;

    @Column(nullable = false, length = 50)
    private String patientRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MotifDemande motif;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GroupeSanguin groupeSanguin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Composant composant;

    @Column(nullable = false)
    private int quantitePoches;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Urgence urgence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatutDemande statut;

    @Column(length = 100)
    private String referenceApprovisionnement;

    /** 2 ou 3 donneurs demandes a la famille, ou null (R17). */
    private Integer compensationDemandee;

    @Builder.Default
    @Column(nullable = false)
    private int compensationRecue = 0;

    @Column(nullable = false)
    private Instant dateCreation;

    @PrePersist
    void avantInsertion() {
        if (dateCreation == null) {
            dateCreation = Instant.now();
        }
    }
}
