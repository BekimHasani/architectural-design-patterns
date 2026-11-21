package com.designpatterns.level09.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * FLOW S.2: typed view of the {@code payment.*} settings in application.yml.
 *
 * <p>{@code @ConfigurationProperties("payment")} tells Spring Boot to fill this record from every key under
 * {@code payment:} ({@code base-urls} → {@code baseUrls}: "relaxed binding", which also lets environment
 * variables override values). It is switched on by {@code @EnableConfigurationProperties} in
 * {@link ApplicationConfig}. Other config classes receive it as a {@code @Bean} method parameter instead of
 * reading strings themselves.
 */
@ConfigurationProperties("payment")
public record PaymentProperties(List<String> enabled, Map<String, String> baseUrls, Routing routing,
                                Resilience resilience, Map<String, Map<String, Map<String, String>>> merchants) {

    public record Routing(String defaultProvider,
                          Map<String, String> byCurrency,
                          Map<String, String> byMerchant,
                          List<String> fallbackOrder) {
    }

    public record Resilience(Duration connectTimeout, Duration readTimeout,
                             int maxAttempts, Duration retryBaseDelay,
                             int breakerFailureThreshold, Duration breakerOpenFor) {
    }
}
