package com.designpatterns.level09.config;

import com.designpatterns.level09.persistence.MerchantConfigRepository;
import com.designpatterns.level09.persistence.config.PropertiesMerchantConfigRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FLOW S.5: persistence wiring. The JPA adapters are {@code @Repository} beans found by scanning; only the
 * merchant store needs configuration data handed to it.
 *
 * <p>Also at startup, with no code of ours: Spring Boot runs {@code schema.sql} to create the tables
 * (Hibernate's {@code ddl-auto} is off, so the SQL file alone owns the schema), and Spring Data generates
 * an implementation for every {@code SpringDataXxx} interface.
 *
 * <p>FLOW S.6: finally Spring builds the {@code @Service}s and {@code @RestController}s by constructor
 * injection, and the web server starts listening. The app is ready for FLOW A.
 */
@Configuration
class PersistenceConfig {

    @Bean
    MerchantConfigRepository merchantConfigRepository(PaymentProperties properties) {
        return new PropertiesMerchantConfigRepository(properties.merchants());
    }
}
