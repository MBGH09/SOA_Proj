package tn.iit.blood.cnts.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.wsdl.wsdl11.SimpleWsdl11Definition;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * Configuration du serveur SOAP Spring-WS.
 *
 * Deux beans enregistrés sous /ws/* :
 *   - "cnts"       → sert cnts.wsdl à /ws/cnts.wsdl  (SimpleWsdl11Definition)
 *   - "cntsSchema" → sert cnts.xsd  à /ws/cntsSchema.xsd (SimpleXsdSchema)
 *
 * Le WSDL référence cnts.xsd via schemaLocation="cnts.xsd".
 * Spring-WS résout ce chemin relatif automatiquement grâce au bean XsdSchema,
 * ce qui permet aux clients SOAP de charger les types sans erreur.
 */
@EnableWs
@Configuration
public class WebServiceConfig {

    /**
     * Monte le dispatcher SOAP sur /ws/*
     * Les appels SOAP   → http://localhost:8084/ws
     * Le WSDL           → http://localhost:8084/ws/cnts.wsdl
     * Le XSD            → http://localhost:8084/ws/cntsSchema.xsd
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
     * Sert le WSDL statique cnts.wsdl.
     * Nom du bean = "cnts" → URL : /ws/cnts.wsdl
     */
    @Bean(name = "cnts")
    public SimpleWsdl11Definition cntsWsdl() {
        return new SimpleWsdl11Definition(new ClassPathResource("cnts.wsdl"));
    }

    /**
     * Expose le XSD à /ws/cntsSchema.xsd
     * Fix P1 : le WSDL importe cnts.xsd — le MessageDispatcherServlet détecte
     * automatiquement les beans XsdSchema et les sert sous /ws/<beanName>.xsd.
     * Les clients SOAP peuvent donc résoudre les types sans erreur 404.
     */
    @Bean(name = "cntsSchema")
    public XsdSchema cntsSchema() {
        return new SimpleXsdSchema(new ClassPathResource("cnts.xsd"));
    }
}
