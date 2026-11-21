package com.designpatterns.level09.service.routing.rules;

import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.service.routing.RoutingContext;
import com.designpatterns.level09.service.routing.RoutingRule;


import java.util.Currency;
import java.util.Map;
import java.util.Optional;

/** EUR → Adyen, USD → Stripe, GBP → PayPal (or whatever configuration says). */
public final class CurrencyRule implements RoutingRule {

    private final Map<Currency, ProviderId> providerByCurrency;

    public CurrencyRule(Map<Currency, ProviderId> providerByCurrency) {
        this.providerByCurrency = Map.copyOf(providerByCurrency);
    }

    @Override
    public Optional<ProviderId> propose(RoutingContext context) {
        return Optional.ofNullable(providerByCurrency.get(context.currency()));
    }
}
