package tn.iit.blood.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entree de l'API Gateway du projet SOA de gestion des dons de sang.
 *
 * <p>Cette classe demarre une application Spring Cloud Gateway reactive
 * (WebFlux / Netty). Le port d'ecoute est configure dans application.yml
 * (8090 par defaut). Les routes metier sont definies en S2-02 ; la securite
 * sera ajoutee dans les phases suivantes.</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
