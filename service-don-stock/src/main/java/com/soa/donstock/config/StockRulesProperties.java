package com.soa.donstock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stock")
public class StockRulesProperties {

    private int soapTimeoutMs = 3000;
    private int retryJobsIntervalSec = 60;
    private int delaiQualificationSec = 20;
    private int delaiLivraisonSec = 30;
    private String cntsUrl = "http://service-cnts:8084/ws";

    public int getSoapTimeoutMs() {
        return soapTimeoutMs;
    }

    public void setSoapTimeoutMs(int soapTimeoutMs) {
        this.soapTimeoutMs = soapTimeoutMs;
    }

    public int getRetryJobsIntervalSec() {
        return retryJobsIntervalSec;
    }

    public void setRetryJobsIntervalSec(int retryJobsIntervalSec) {
        this.retryJobsIntervalSec = retryJobsIntervalSec;
    }

    public int getDelaiQualificationSec() {
        return delaiQualificationSec;
    }

    public void setDelaiQualificationSec(int delaiQualificationSec) {
        this.delaiQualificationSec = delaiQualificationSec;
    }

    public int getDelaiLivraisonSec() {
        return delaiLivraisonSec;
    }

    public void setDelaiLivraisonSec(int delaiLivraisonSec) {
        this.delaiLivraisonSec = delaiLivraisonSec;
    }

    public String getCntsUrl() {
        return cntsUrl;
    }

    public void setCntsUrl(String cntsUrl) {
        this.cntsUrl = cntsUrl;
    }
}
