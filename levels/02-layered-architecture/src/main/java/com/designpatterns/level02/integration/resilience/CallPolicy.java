package com.designpatterns.level02.integration.resilience;

import com.designpatterns.level02.integration.gateway.GatewayResult;

import java.util.function.Supplier;

/** Wraps one provider call: retry, circuit breaker, … A decorator around the CALL, not around an interface. */
@FunctionalInterface
public interface CallPolicy {

    GatewayResult run(Supplier<GatewayResult> call);
}
