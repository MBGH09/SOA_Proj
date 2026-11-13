package tn.iit.blood.service_don_stock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServiceDonStockApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceDonStockApplication.class, args);
    }
}
