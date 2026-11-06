package com.service_demandes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "appels_don")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppelDon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "demande_id", nullable = false)
    private Demande demande;

    @Column(nullable = false)
    private String groupeSanguin;

    @Column(nullable = false)
    private String composant;

    @Enumerated(EnumType.STRING)
    private StatutAppelDon statut;

    private LocalDateTime dateCreation;

    @PrePersist
    public void prePersist() {
        this.dateCreation = LocalDateTime.now();
    }
}