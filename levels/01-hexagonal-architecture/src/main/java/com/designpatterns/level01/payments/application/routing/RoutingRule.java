package com.designpatterns.level01.payments.application.routing;

import com.designpatterns.level01.payments.domain.ProviderId;

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
