package tn.iit.blood.cnts.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.wsdl.wsdl11.SimpleWsdl11Definition;
import org.springframework.ws.transport.http.MessageDispatcherServlet;

/**
 * Configuration du serveur SOAP Spring-WS.
 *
 * Changement S5-03 (correctif contrat) :
 *   On passe de DefaultWsdl11Definition (génération auto) à
 *   SimpleWsdl11Definition (WSDL statique).
 *
 * Raison : le générateur automatique crée les faults comme des
 * opérations autonomes sans input/output. Le WSDL statique
 * cnts.wsdl déclare correctement chaque fault sous l'opération
 * qui la produit (PrelevementInvalide → enregistrerPrelevement,
 * StockCentralInsuffisant → demanderApprovisionnement,
 * CertificatRefuse → delivrerCertificatDon).
 */
@EnableWs
@Configuration
public class WebServiceConfig {

    /**
     * Monte le dispatcher SOAP sur /ws/*
     * → les appels SOAP arrivent sur http://localhost:8084/ws
     * → le WSDL est disponible sur http://localhost:8084/ws/cnts.wsdl
     */
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        // Réécrit l'adresse du service dans le WSDL selon l'hôte réel
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    /**
     * Sert le WSDL statique cnts.wsdl depuis src/main/resources/.
     * Le nom du bean "cnts" détermine l'URL : /ws/cnts.wsdl
     *
     * Le WSDL statique déclare correctement les faults SOAP
     * attachées à chaque opération dans le portType et le binding.
     */
    @Bean(name = "cnts")
    public SimpleWsdl11Definition cntsWsdl() {
        return new SimpleWsdl11Definition(new ClassPathResource("cnts.wsdl"));
    }
}
