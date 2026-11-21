package com.designpatterns.level09.config;

import com.designpatterns.level09.integration.gateway.ProviderGateway;
import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.service.routing.RoutingRule;
import com.designpatterns.level09.service.routing.RoutingRules;
import com.designpatterns.level09.service.routing.rules.CurrencyRule;
import com.designpatterns.level09.service.routing.rules.DefaultRule;
import com.designpatterns.level09.service.routing.rules.MerchantOverrideRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FLOW S.4: turns routing config (data) into rule objects (code). PRECEDENCE IS THE ORDER OF THIS LIST.
 *
 * <p>It returns a {@code RoutingRules} wrapper, not a bare {@code List<RoutingRule>}: for a list parameter
 * Spring would collect every {@code RoutingRule} bean on its own, in no defined order. A dedicated type
 * says "exactly this list, in this order".
 */
@Configuration
class RoutingConfig {

    @Bean
    RoutingRules routingRules(PaymentProperties properties, ProviderGateway gateway) {
        PaymentProperties.Routing routing = properties.routing();
        failFastOnUnknownProviders(routing, gateway);
        List<RoutingRule> rules = new ArrayList<>(List.of(
                new MerchantOverrideRule(toIds(routing.byMerchant())),
                new CurrencyRule(toIds(routing.byCurrency()).entrySet().stream()
                        .collect(Collectors.toMap(e -> Currency.getInstance(e.getKey()), Map.Entry::getValue))),
                new DefaultRule(ProviderId.of(routing.defaultProvider()))));
        // Fallback candidates go last: they are only used when the ones before them fail safely.
        routing.fallbackOrder().forEach(id -> rules.add(new DefaultRule(ProviderId.of(id))));
        return new RoutingRules(rules);
    }

    /** A typo in the config ("stirpe") should stop the app at startup, not fail a customer's payment. */
    private static void failFastOnUnknownProviders(PaymentProperties.Routing routing, ProviderGateway gateway) {
        Set<String> referenced = new HashSet<>(routing.byMerchant().values());
        referenced.addAll(routing.byCurrency().values());
        referenced.add(routing.defaultProvider());
        referenced.addAll(routing.fallbackOrder());
        Set<String> unknown = referenced.stream()
                .filter(id -> gateway.descriptors().stream().noneMatch(d -> d.id().equals(ProviderId.of(id))))
                .collect(Collectors.toSet());
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("payment.routing refers to providers that are not enabled: " + unknown);
        }
    }

    private static Map<String, ProviderId> toIds(Map<String, String> config) {
        return config.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> ProviderId.of(e.getValue())));
    }
}
