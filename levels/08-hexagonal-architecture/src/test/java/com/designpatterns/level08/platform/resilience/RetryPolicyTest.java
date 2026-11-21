package com.designpatterns.level08.platform.resilience;

import com.designpatterns.level08.payments.application.ports.FailureCategory;
import com.designpatterns.level08.payments.application.ports.GatewayResult;
import com.designpatterns.level08.payments.domain.DeclineReason;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private static final GatewayResult UNAVAILABLE = new GatewayResult.Failed(FailureCategory.PROVIDER_UNAVAILABLE, "503");
    private static final GatewayResult OK = new GatewayResult.Authorized("ref");

    private final AtomicInteger calls = new AtomicInteger();
    private final RetryPolicy retry = new RetryPolicy(3, Duration.ofMillis(1));

    private Supplier<GatewayResult> script(GatewayResult... results) {
        Deque<GatewayResult> left = new ArrayDeque<>(List.of(results));
        return () -> {
            calls.incrementAndGet();
            return left.size() > 1 ? left.removeFirst() : left.peekFirst();
        };
    }

    @Test
    void retriesSafeFailuresUntilSuccess() {
        assertThat(retry.run(script(UNAVAILABLE, UNAVAILABLE, OK))).isEqualTo(OK);
        assertThat(calls).hasValue(3);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        assertThat(retry.run(script(UNAVAILABLE))).isEqualTo(UNAVAILABLE);
        assertThat(calls).hasValue(3);
    }

    @Test
    void neverRetriesUnknownOutcomesOrDeclines() {
        retry.run(script(new GatewayResult.Failed(FailureCategory.OUTCOME_UNKNOWN, "timeout"), OK));
        retry.run(script(new GatewayResult.Declined(DeclineReason.GENERIC), OK));

        assertThat(calls).hasValue(2);
    }
}
