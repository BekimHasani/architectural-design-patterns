package com.designpatterns.level09.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Cross-cutting beans. With {@code @Service}/{@code @Repository}/{@code @Component} scanning, most classes
 * wire themselves; config only builds what needs CONFIGURATION DATA or a choice between implementations.
 * Compare with Level 8's PlatformConfig, which constructed every use case by hand.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(PaymentProperties.class)
class ApplicationConfig {

    /**
     * Injected everywhere time matters, so tests can fix it.
     * {@code @Bean}: Spring calls this method once and shares the returned object with every class
     * that asks for a {@code Clock} in its constructor.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
