package com.designpatterns.level09.integration.resilience;

import com.designpatterns.level09.integration.gateway.GatewayResult;


import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Retries a call, only on failures that prove nothing was charged, with exponential backoff and
 * full jitter. In Level 7 this was a decorator of one big interface. With capability interfaces a
 * decorator would need a version per combination, so it wraps the CALL instead.
 */
public final class RetryPolicy implements CallPolicy {

    private final int maxAttempts;
    private final Duration baseDelay;

    public RetryPolicy(int maxAttempts, Duration baseDelay) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
    }

    @Override
    public GatewayResult run(Supplier<GatewayResult> call) {
        // FLOW A.10b: try once; repeat ONLY while the failure proves nothing was charged. A timeout
        // (OUTCOME_UNKNOWN) is never retried here: that is reconciliation's job (FLOW D).
        GatewayResult result = call.get();
        for (int attempt = 1; attempt < maxAttempts && isRetryable(result); attempt++) {
            sleep(backoff(attempt));
            result = call.get();
        }
        return result;
    }

    private static boolean isRetryable(GatewayResult result) {
        return result instanceof GatewayResult.Failed failed && failed.category().retryable();
    }

    private Duration backoff(int attempt) {
        long ceiling = baseDelay.toMillis() << (attempt - 1);
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(ceiling + 1));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
