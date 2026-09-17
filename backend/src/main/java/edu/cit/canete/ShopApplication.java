package edu.cit.canete;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Parent package application class.
 * Sits above both edu.cit.canete.shop and edu.cit.canete.inventory
 * so component scanning picks up both modules.
 */
@SpringBootApplication
public class ShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
