package com.designpatterns.level01.payments.application.routing.rules;

import com.designpatterns.level01.payments.application.routing.RoutingContext;
import com.designpatterns.level01.payments.application.routing.RoutingRule;
import com.designpatterns.level01.payments.domain.ProviderId;

import java.util.Optional;

/** Always applies. Put it last. */
public final class DefaultRule implements RoutingRule {

    private final ProviderId defaultProvider;

    public DefaultRule(ProviderId defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    @Override
    public Optional<ProviderId> propose(RoutingContext context) {
        return Optional.of(defaultProvider);
    }
}
