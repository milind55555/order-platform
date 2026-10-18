package com.orderplatform.inventory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

/** Seeds a small catalogue on first start so the demo works out of the box. */
@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner seedProducts(ProductRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                repository.saveAll(List.of(
                        new Product("LAPTOP-001", "Developer Laptop", new BigDecimal("75000.00"), 50),
                        new Product("PHONE-001", "Smartphone", new BigDecimal("25000.00"), 100),
                        new Product("HEADSET-001", "Noise Cancelling Headphones", new BigDecimal("3000.00"), 200),
                        new Product("KEYBOARD-001", "Mechanical Keyboard", new BigDecimal("1500.00"), 300)));
            }
        };
    }
}
