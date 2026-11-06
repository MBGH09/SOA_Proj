package com.service_demandes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "compensations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Compensation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "demande_id", nullable = false)
    private Demande demande;

    @Column(nullable = false, unique = true)
    private String numeroCertificat;

    private LocalDateTime dateEnregistrement;

    @PrePersist
    public void prePersist() {
        this.dateEnregistrement = LocalDateTime.now();
    }
}