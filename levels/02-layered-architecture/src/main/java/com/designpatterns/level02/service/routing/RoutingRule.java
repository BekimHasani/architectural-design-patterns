package com.designpatterns.level02.service.routing;

import com.designpatterns.level02.model.ProviderId;


import java.util.Optional;

/**
 * STRATEGY: one routing rule, one reason to choose a provider. Rules carry their data
 * (from configuration) but contain the logic as code. Adding a rule = adding a class; the
 * router and the checkout don't change.
 */
public interface RoutingRule {

    /** The provider this rule wants for the payment, or empty if the rule doesn't apply. */
    Optional<ProviderId> propose(RoutingContext context);
}
