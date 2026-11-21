package com.designpatterns.level02.integration.gateway;

/**
 * THE CLASSIFICATION TABLE. Adapters translate provider errors into one of these; everything
 * else (retry decorator, circuit breaker, fallback) only asks the three questions below and
 * never looks at an HTTP status or a provider error code.
 *
 * <pre>
 *                         may have charged?  retry same?  fall back?
 * INVALID_REQUEST         no                 no           no   (our bug/config: another provider won't fix it)
 * RATE_LIMITED            no                 yes          yes
 * PROVIDER_UNAVAILABLE    no                 yes          yes  (503, connection refused, circuit open)
 * OUTCOME_UNKNOWN         YES                no           NO   (timeout after send, 500, garbage) → reconcile
 * NOT_PROCESSED           no                 —            —    (only from getStatus: provider has no record)
 * </pre>
 * Declines are not here: they are {@link GatewayResult.Declined}, never retried, never routed elsewhere.
 * NOT_PROCESSED must never be confused with PROVIDER_UNAVAILABLE: "the lookup failed" means
 * we still don't know; "the provider has no such attempt" means nothing was charged.
 */
public enum FailureCategory {
    INVALID_REQUEST(false, false),
    RATE_LIMITED(true, true),
    PROVIDER_UNAVAILABLE(true, true),
    OUTCOME_UNKNOWN(false, false),
    NOT_PROCESSED(false, false);

    private final boolean retryable;
    private final boolean safeToFallback;

    FailureCategory(boolean retryable, boolean safeToFallback) {
        this.retryable = retryable;
        this.safeToFallback = safeToFallback;
    }

    /** Trying the same provider again cannot cause a second charge. */
    public boolean retryable() {
        return retryable;
    }

    /** We KNOW nothing was charged, so another provider may be tried. */
    public boolean safeToFallback() {
        return safeToFallback;
    }

    /** The provider may or may not have charged. Never guess. */
    public boolean outcomeUnknown() {
        return this == OUTCOME_UNKNOWN;
    }
}
