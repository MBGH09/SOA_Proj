package com.service_demandes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demandes")
public class DemandesRulesProperties {

    private int compensationMin = 2;
    private int compensationMax = 3;
    private int grpcDeadlineMs = 2000;
    private int retryJobsIntervalSec = 60;
    private String donStockGrpcTarget = "static://service-don-stock:9090";
    private String donneursUrl = "http://service-donneurs:8081";

    public int getCompensationMin() {
        return compensationMin;
    }

    public void setCompensationMin(int compensationMin) {
        this.compensationMin = compensationMin;
    }

    public int getCompensationMax() {
        return compensationMax;
    }

    public void setCompensationMax(int compensationMax) {
        this.compensationMax = compensationMax;
    }

    public int getGrpcDeadlineMs() {
        return grpcDeadlineMs;
    }

    public void setGrpcDeadlineMs(int grpcDeadlineMs) {
        this.grpcDeadlineMs = grpcDeadlineMs;
    }

    public int getRetryJobsIntervalSec() {
        return retryJobsIntervalSec;
    }

    public void setRetryJobsIntervalSec(int retryJobsIntervalSec) {
        this.retryJobsIntervalSec = retryJobsIntervalSec;
    }

    public String getDonStockGrpcTarget() {
        return donStockGrpcTarget;
    }

    public void setDonStockGrpcTarget(String donStockGrpcTarget) {
        this.donStockGrpcTarget = donStockGrpcTarget;
    }

    public String getDonneursUrl() {
        return donneursUrl;
    }

    public void setDonneursUrl(String donneursUrl) {
        this.donneursUrl = donneursUrl;
    }
}
