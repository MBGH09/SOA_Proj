package com.service_demandes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entree. La classe est a la racine du package com.service_demandes
 * pour que les entites, depots, services et controleurs des sous-packages soient trouves.
 */
@SpringBootApplication
public class ServiceDemandesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceDemandesApplication.class, args);
    }
}
