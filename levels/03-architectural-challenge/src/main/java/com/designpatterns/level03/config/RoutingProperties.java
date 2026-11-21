package com.designpatterns.level03.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/** {@code payment.routing.*} in application.yml. */
@ConfigurationProperties("payment.routing")
public record RoutingProperties(List<String> defaultOrder, Map<String, String> preferredByCurrency) {

    public RoutingProperties {
        defaultOrder = defaultOrder == null ? List.of() : defaultOrder;
        preferredByCurrency = preferredByCurrency == null ? Map.of() : preferredByCurrency;
    }
}
