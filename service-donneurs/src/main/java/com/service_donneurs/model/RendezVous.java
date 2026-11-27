package com.service_donneurs.model;

import jakarta.persistence.*;

@Entity
@Table(name = "rendez_vous")
public class RendezVous {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "donneur_id")
    private Donneur donneur;

    @ManyToOne
    @JoinColumn(name = "centre_id")
    private Centre centre;

    @ManyToOne
    @JoinColumn(name = "creneau_id")
    private Creneau creneau;

    private String canal;

    private String statut;

    public RendezVous() {
    }

    public Long getId() {
        return id;
    }

    public Donneur getDonneur() {
        return donneur;
    }

    public void setDonneur(Donneur donneur) {
        this.donneur = donneur;
    }

    public Centre getCentre() {
        return centre;
    }

    public void setCentre(Centre centre) {
        this.centre = centre;
    }

    public Creneau getCreneau() {
        return creneau;
    }

    public void setCreneau(Creneau creneau) {
        this.creneau = creneau;
    }

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }
}