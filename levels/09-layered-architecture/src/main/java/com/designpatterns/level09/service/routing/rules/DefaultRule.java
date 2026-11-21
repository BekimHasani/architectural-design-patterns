package com.designpatterns.level09.service.routing.rules;

import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.service.routing.RoutingContext;
import com.designpatterns.level09.service.routing.RoutingRule;


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
