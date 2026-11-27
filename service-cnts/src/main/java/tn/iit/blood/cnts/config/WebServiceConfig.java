package tn.iit.blood.cnts.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

import java.util.List;

/**
 * Configuration du serveur SOAP Spring-WS — S5-03 / S5-08.
 *
 * S5-03 : DefaultWsdl11Definition génère le WSDL automatiquement depuis le XSD.
 *         Les 3 opérations sont visibles à /ws/cnts.wsdl.
 * S5-08 : PayloadValidatingInterceptor valide chaque requête SOAP contre cnts.xsd
 *         avant qu'elle n'atteigne l'endpoint. Un XML invalide retourne une
 *         SOAP:Fault automatique sans passer par le code métier.
 */
@EnableWs
@Configuration
public class WebServiceConfig implements WsConfigurer {

    /**
     * S5-08 — Enregistre le PayloadValidatingInterceptor pour toutes les opérations.
     * Les requêtes malformées (groupe inconnu, champ manquant) sont rejetées ici
     * avant d'atteindre CntsEndpoint.
     */
    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        interceptors.add(payloadValidatingInterceptor());
    }

    /**
     * S5-08 — Intercepteur de validation XSD.
     * validateRequest  = true  : filtre les requêtes entrantes.
     * validateResponse = false : les réponses de fault ne suivent pas le schéma
     *                            de réponse et ne doivent pas être re-validées.
     */
    @Bean
    public PayloadValidatingInterceptor payloadValidatingInterceptor() {
        PayloadValidatingInterceptor interceptor = new PayloadValidatingInterceptor();
        interceptor.setSchema(new ClassPathResource("cnts.xsd"));
        interceptor.setValidateRequest(true);
        interceptor.setValidateResponse(false);
        return interceptor;
    }

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
