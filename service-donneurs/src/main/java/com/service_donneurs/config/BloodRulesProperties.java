package com.service_donneurs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "blood")
public class BloodRulesProperties {

    private int ageMin = 18;
    private int ageMax = 70;
    private int agePremierDonMax = 65;
    private int poidsMinKg = 50;
    private int volumePrelevementMl = 450;
    private int reposMinMinutes = 15;
    private int creneauCapaciteDefaut = 4;
    private int retryJobsIntervalSec = 60;
    private int grpcDeadlineMs = 2000;
    private String donStockGrpcTarget = "static://service-don-stock:9090";

    public int getAgeMin() {
        return ageMin;
    }

    public void setAgeMin(int ageMin) {
        this.ageMin = ageMin;
    }

    public int getAgeMax() {
        return ageMax;
    }

    public void setAgeMax(int ageMax) {
        this.ageMax = ageMax;
    }

    public int getAgePremierDonMax() {
        return agePremierDonMax;
    }

    public void setAgePremierDonMax(int agePremierDonMax) {
        this.agePremierDonMax = agePremierDonMax;
    }

    public int getPoidsMinKg() {
        return poidsMinKg;
    }

    public void setPoidsMinKg(int poidsMinKg) {
        this.poidsMinKg = poidsMinKg;
    }

    public int getVolumePrelevementMl() {
        return volumePrelevementMl;
    }

    public void setVolumePrelevementMl(int volumePrelevementMl) {
        this.volumePrelevementMl = volumePrelevementMl;
    }

    public int getReposMinMinutes() {
        return reposMinMinutes;
    }

    public void setReposMinMinutes(int reposMinMinutes) {
        this.reposMinMinutes = reposMinMinutes;
    }

    public int getCreneauCapaciteDefaut() {
        return creneauCapaciteDefaut;
    }

    public void setCreneauCapaciteDefaut(int creneauCapaciteDefaut) {
        this.creneauCapaciteDefaut = creneauCapaciteDefaut;
    }

    public int getRetryJobsIntervalSec() {
        return retryJobsIntervalSec;
    }

    public void setRetryJobsIntervalSec(int retryJobsIntervalSec) {
        this.retryJobsIntervalSec = retryJobsIntervalSec;
    }

    public int getGrpcDeadlineMs() {
        return grpcDeadlineMs;
    }

    public void setGrpcDeadlineMs(int grpcDeadlineMs) {
        this.grpcDeadlineMs = grpcDeadlineMs;
    }

    public String getDonStockGrpcTarget() {
        return donStockGrpcTarget;
    }

    public void setDonStockGrpcTarget(String donStockGrpcTarget) {
        this.donStockGrpcTarget = donStockGrpcTarget;
    }
}
