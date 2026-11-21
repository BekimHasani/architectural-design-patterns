package com.designpatterns.level02.service.routing.rules;

import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.service.routing.RoutingContext;
import com.designpatterns.level02.service.routing.RoutingRule;


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
