/**
 * <h2>Resilience: what to do when a provider is slow or down.</h2>
 *
 * <p>Every provider call runs as {@code breaker( retry( call ) )}, built in {@code config.IntegrationConfig}.
 * <ul>
 *   <li><b>Decorator</b>: each {@link com.designpatterns.level02.integration.resilience.CallPolicy} wraps a
 *       call and adds behaviour, without the call knowing. We wrap the CALL (a {@code Supplier}), not the
 *       adapter, so one policy works for every capability interface.</li>
 *   <li><b>Retry with backoff + jitter</b> ({@code RetryPolicy}): only for failures that PROVE nothing was
 *       charged (rate limited, unavailable). Random waits stop all clients retrying at the same moment.</li>
 *   <li><b>Circuit Breaker</b> ({@code CircuitBreakerPolicy}): a small state machine CLOSED → OPEN → HALF_OPEN.
 *       After N failures it stops calling the provider for a while and answers "unavailable" at once,
 *       which is safe to fall back on because nothing was sent.</li>
 * </ul>
 * A {@code Clock} is injected so tests can move time forward instead of sleeping.
 */
package com.designpatterns.level02.integration.resilience;
