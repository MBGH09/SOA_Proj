package com.service_demandes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "demandes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Demande {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String hopitalId;

    @Column(nullable = false)
    private String hopitalNom;

    @Column(nullable = false)
    private String patientRef;

    private String motif;

    @Column(nullable = false)
    private String groupeSanguin;

    @Column(nullable = false)
    private String composant;

    @Column(nullable = false)
    private Integer quantitePoches;

    @Column(nullable = false)
    private String urgence;

    @Enumerated(EnumType.STRING)
    private StatutDemande statut;

    private Integer compensationDemandee;

    private Integer compensationRecue;

    @Column(nullable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    public void prePersist() {
        this.dateCreation = LocalDateTime.now();
    }

}