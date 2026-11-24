package com.service_donneurs.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "donneur")
public class Donneur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 8)
    private String cin;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(name = "date_naissance", nullable = false)
    private LocalDate dateNaissance;

    @Column(name = "poids_kg", nullable = false)
    private Integer poidsKg;

    @Column(name = "groupe_sanguin", nullable = false)
    private String groupeSanguin;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCin() {
        return cin;
    }

    public void setCin(String cin) {
        this.cin = cin;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public LocalDate getDateNaissance() {
        return dateNaissance;
    }

    public void setDateNaissance(LocalDate dateNaissance) {
        this.dateNaissance = dateNaissance;
    }

    public Integer getPoidsKg() {
        return poidsKg;
    }

    public void setPoidsKg(Integer poidsKg) {
        this.poidsKg = poidsKg;
    }

    public String getGroupeSanguin() {
        return groupeSanguin;
    }

    public void setGroupeSanguin(String groupeSanguin) {
        this.groupeSanguin = groupeSanguin;
    }

    public String getVille() {
        return ville;
    }

    public void setVille(String ville) {
        this.ville = ville;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public Boolean getPremierDon() {
        return premierDon;
    }

    public void setPremierDon(Boolean premierDon) {
        this.premierDon = premierDon;
    }

    private String ville;

    private String telephone;

    @Column(name = "premier_don")
    private Boolean premierDon;

    public Donneur() {
    }

    // Generate Getters and Setters
}