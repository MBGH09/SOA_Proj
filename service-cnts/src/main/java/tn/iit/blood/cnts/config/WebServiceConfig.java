package tn.iit.blood.cnts.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

@EnableWs
@Configuration
public class WebServiceConfig extends WsConfigurerAdapter {

    /**
     * Mounts the SOAP dispatcher on /ws/*
     * So every SOAP call goes to http://localhost:8084/ws
     * and the WSDL is at http://localhost:8084/ws/cnts.wsdl
     */
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        // Rewrites the WSDL location to match the actual server address
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    /**
     * Generates the WSDL automatically from the XSD.
     * The bean name "cnts" determines the WSDL URL: /ws/cnts.wsdl
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
     * Loads the XSD from src/main/resources/cnts.xsd
     */
    @Bean
    public XsdSchema cntsSchema() {
        return new SimpleXsdSchema(new ClassPathResource("cnts.xsd"));
    }
}

