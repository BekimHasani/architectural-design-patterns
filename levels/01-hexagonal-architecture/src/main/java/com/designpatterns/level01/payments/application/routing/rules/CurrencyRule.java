package com.designpatterns.level01.payments.application.routing.rules;

import com.designpatterns.level01.payments.application.routing.RoutingContext;
import com.designpatterns.level01.payments.application.routing.RoutingRule;
import com.designpatterns.level01.payments.domain.ProviderId;

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
