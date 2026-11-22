package tn.iit.blood.service_don_stock.soap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

import java.time.Duration;

/**
 * S4-12 — Spring-WS configuration for the CNTS SOAP client.
 *
 * Uses HttpUrlConnectionMessageSender (JDK built-in, no extra dependency)
 * instead of HttpComponentsMessageSender (which requires Apache HttpClient 4.x,
 * not compatible with the HttpClient 5 that Spring Boot 3.x ships).
 *
 * The Jaxb2Marshaller uses the package generated from cnts.xsd by the
 * jaxb-maven-plugin: tn.iit.blood.donstock.soap.client
 */
@Configuration
public class CntsServiceConfig {

    @Value("${soap.timeout.ms:3000}")
    private int soapTimeoutMs;

    @Bean
    public Jaxb2Marshaller cntsMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("tn.iit.blood.donstock.soap.client");
        return marshaller;
    }

    @Bean
    public HttpUrlConnectionMessageSender cntsHttpSender() {
        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();
        sender.setConnectionTimeout(Duration.ofMillis(soapTimeoutMs));
        sender.setReadTimeout(Duration.ofMillis(soapTimeoutMs));
        return sender;
    }

    @Bean
    public WebServiceTemplate cntsWebServiceTemplate(
            Jaxb2Marshaller cntsMarshaller,
            HttpUrlConnectionMessageSender cntsHttpSender) {

        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(cntsMarshaller);
        template.setUnmarshaller(cntsMarshaller);
        template.setMessageSender(cntsHttpSender);
        return template;
    }
}
