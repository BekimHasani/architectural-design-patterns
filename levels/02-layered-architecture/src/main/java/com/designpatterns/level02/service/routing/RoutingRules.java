package com.designpatterns.level02.service.routing;

import java.util.List;

/**
 * The routing rules IN PRECEDENCE ORDER. A small wrapper so the order is one explicit bean built by
 * {@code config.RoutingConfig}, instead of whatever order Spring injects a {@code List<RoutingRule>} in.
 */
public record RoutingRules(List<RoutingRule> inOrder) {

    public RoutingRules {
        inOrder = List.copyOf(inOrder);
    }
}
