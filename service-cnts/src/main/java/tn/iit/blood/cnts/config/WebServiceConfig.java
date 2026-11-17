package tn.iit.blood.cnts.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * Configuration du serveur SOAP Spring-WS — S5-03 (conforme au guide).
 *
 * Le guide demande DefaultWsdl11Definition : génère le WSDL automatiquement
 * depuis le XSD. Les 3 opérations sont visibles à /ws/cnts.wsdl.
 *
 * Note : la configuration détaillée des Faults est réservée à S5-07.
 */
@EnableWs
@Configuration
public class WebServiceConfig {

    /**
     * Monte le dispatcher SOAP sur /ws/*
     * → appels SOAP : http://localhost:8084/ws
     * → WSDL auto   : http://localhost:8084/ws/cnts.wsdl
     */
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    /**
     * Génère le WSDL automatiquement depuis cnts.xsd (S5-03, conforme au guide).
     * Nom du bean = "cnts" → URL : /ws/cnts.wsdl
     *
     * DefaultWsdl11Definition détecte les éléments du XSD dont le nom termine par
     * "Request" / "Response" et crée les 3 opérations correspondantes :
     *   enregistrerPrelevement, demanderApprovisionnement, delivrerCertificatDon
     */
    @Bean(name = "cnts")
    public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema cntsSchema) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("CntsPort");
        wsdl.setLocationUri("/ws");
        wsdl.setTargetNamespace("http://iit.tn/soa/cnts");
        wsdl.setSchema(cntsSchema);
        return wsdl;
    }

    /**
     * Charge cnts.xsd depuis le classpath.
     * Le XSD est copié dans les ressources du service via la section <resources>
     * du pom.xml (depuis contracts/wsdl-xsd/cnts.xsd).
     */
    @Bean
    public XsdSchema cntsSchema() {
        return new SimpleXsdSchema(new ClassPathResource("cnts.xsd"));
    }
}
