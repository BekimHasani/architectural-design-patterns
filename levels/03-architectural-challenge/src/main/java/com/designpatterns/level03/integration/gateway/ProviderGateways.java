package com.designpatterns.level03.integration.gateway;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** REGISTRY: every {@link PaymentGateway} bean, looked up by provider id. */
@Component
public class ProviderGateways {

    private final Map<String, PaymentGateway> byId;

    public ProviderGateways(List<PaymentGateway> gateways) {
        this.byId = gateways.stream().collect(Collectors.toMap(PaymentGateway::id, Function.identity()));
    }

    public PaymentGateway get(String providerId) {
        PaymentGateway gateway = byId.get(providerId);
        if (gateway == null) {
            throw new IllegalStateException("No gateway for provider " + providerId);
        }
        return gateway;
    }
}
