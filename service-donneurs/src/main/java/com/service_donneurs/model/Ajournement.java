package com.service_donneurs.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "ajournement")
public class Ajournement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "donneur_id")
    private Donneur donneur;

    @Column(nullable = false)
    private String motif;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    public Ajournement() {
    }

    // Generate Getters and Setters
}