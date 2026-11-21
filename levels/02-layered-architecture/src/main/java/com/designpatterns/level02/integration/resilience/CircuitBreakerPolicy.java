package com.designpatterns.level02.integration.resilience;

import com.designpatterns.level02.integration.gateway.FailureCategory;
import com.designpatterns.level02.integration.gateway.GatewayResult;


import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Stops calling a provider that is clearly down. One instance per provider. While OPEN, calls fail
 * at once with PROVIDER_UNAVAILABLE: no request was sent, so falling back is safe.
 */
public final class CircuitBreakerPolicy implements CallPolicy {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final Duration openFor;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt = Instant.MIN;

    public CircuitBreakerPolicy(int failureThreshold, Duration openFor, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openFor = openFor;
        this.clock = clock;
    }

    @Override
    public GatewayResult run(Supplier<GatewayResult> call) {
        // FLOW A.10a: the outermost wrapper. OPEN → don't even call (fail fast, safe to fall back);
        // CLOSED or HALF_OPEN (one trial call after the pause) → call and remember how it went.
        if (state() == State.OPEN) {
            return new GatewayResult.Failed(FailureCategory.PROVIDER_UNAVAILABLE, "Circuit open: not called");
        }
        GatewayResult result = call.get();
        record(result);
        return result;
    }

    public synchronized State state() {
        if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(openFor))) {
            state = State.HALF_OPEN;
        }
        return state;
    }

    private synchronized void record(GatewayResult result) {
        boolean providerFailure = result instanceof GatewayResult.Failed f
                && (f.category() == FailureCategory.PROVIDER_UNAVAILABLE || f.category().outcomeUnknown());
        if (!providerFailure) {
            state = State.CLOSED;
            consecutiveFailures = 0;
        } else if (state == State.HALF_OPEN || ++consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = clock.instant();
        }
    }
}
