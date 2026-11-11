package com.service_demandes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/** Appel public aux dons, ouvert en cas de penurie ou a la main. */
@Entity
@Table(name = "appel_don")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AppelDon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Facultative : un appel peut etre ouvert sans demande. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_id")
    private Demande demande;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GroupeSanguin groupeSanguin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Composant composant;

    @Column(length = 100)
    private String centreCibleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutAppelDon statut;

    @Column(nullable = false)
    private Instant dateOuverture;

    private Instant dateFermeture;

    @PrePersist
    void avantInsertion() {
        if (dateOuverture == null) {
            dateOuverture = Instant.now();
        }
    }
}
