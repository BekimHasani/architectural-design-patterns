package com.designpatterns.level01.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Typed view of the {@code payment.*} settings in application.yml. */
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
