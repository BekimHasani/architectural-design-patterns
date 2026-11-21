package com.designpatterns.level01.config;

import com.designpatterns.level01.payments.application.ports.GatewayRegistry;
import com.designpatterns.level01.payments.application.routing.ProviderRouter;
import com.designpatterns.level01.payments.application.routing.RoutingRule;
import com.designpatterns.level01.payments.application.routing.rules.CurrencyRule;
import com.designpatterns.level01.payments.application.routing.rules.DefaultRule;
import com.designpatterns.level01.payments.application.routing.rules.MerchantOverrideRule;
import com.designpatterns.level01.payments.domain.ProviderId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Turns routing config (data) into rule objects (code). PRECEDENCE IS THE ORDER OF THIS LIST. */
@Configuration
class RoutingConfig {

    @Bean
    ProviderRouter providerRouter(PaymentProperties properties, GatewayRegistry registry) {
        PaymentProperties.Routing routing = properties.routing();
        List<RoutingRule> rules = new ArrayList<>(List.of(
                new MerchantOverrideRule(toIds(routing.byMerchant())),
                new CurrencyRule(toIds(routing.byCurrency()).entrySet().stream()
                        .collect(Collectors.toMap(e -> Currency.getInstance(e.getKey()), Map.Entry::getValue))),
                new DefaultRule(ProviderId.of(routing.defaultProvider()))));
        // Fallback candidates go last: they are only used when the ones before them fail safely.
        routing.fallbackOrder().forEach(id -> rules.add(new DefaultRule(ProviderId.of(id))));

        ProviderRouter router = new ProviderRouter(
                rules, registry.descriptors()); // what each provider can do comes from the provider itself
        failFastOnUnknownProviders(routing, registry);
        return router;
    }

    /** A typo in the config ("stirpe") should stop the app at startup, not fail a customer's payment. */
    private static void failFastOnUnknownProviders(PaymentProperties.Routing routing, GatewayRegistry registry) {
        Set<String> referenced = new HashSet<>(routing.byMerchant().values());
        referenced.addAll(routing.byCurrency().values());
        referenced.add(routing.defaultProvider());
        referenced.addAll(routing.fallbackOrder());
        Set<String> unknown = referenced.stream()
                .filter(id -> registry.descriptors().stream().noneMatch(d -> d.id().equals(ProviderId.of(id))))
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
