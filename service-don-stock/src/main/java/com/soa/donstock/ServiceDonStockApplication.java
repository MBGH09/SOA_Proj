package com.soa.donstock;

import com.soa.donstock.config.StockRulesProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StockRulesProperties.class)
public class ServiceDonStockApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceDonStockApplication.class, args);
    }
}
