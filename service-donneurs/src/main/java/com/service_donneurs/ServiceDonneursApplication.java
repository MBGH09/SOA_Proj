package com.service_donneurs;

import com.service_donneurs.config.BloodRulesProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BloodRulesProperties.class)
public class ServiceDonneursApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServiceDonneursApplication.class, args);
	}

}
