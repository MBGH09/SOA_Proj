package com.service_demandes;

import com.service_demandes.config.DemandesRulesProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée. La classe est à la racine du package com.service_demandes
 * pour que les entités, dépôts, services et contrôleurs des sous-packages soient trouvés.
 */
@SpringBootApplication
@EnableConfigurationProperties(DemandesRulesProperties.class)
@EnableScheduling
public class ServiceDemandesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceDemandesApplication.class, args);
    }
}
