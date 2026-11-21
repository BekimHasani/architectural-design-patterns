package com.designpatterns.level08.platform.resilience;

import com.designpatterns.level08.payments.application.ports.FailureCategory;
import com.designpatterns.level08.payments.application.ports.GatewayResult;
import com.designpatterns.level08.payments.domain.DeclineReason;
import com.designpatterns.level08.platform.resilience.CircuitBreakerPolicy.State;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerPolicyTest {

    private static final GatewayResult UNAVAILABLE = new GatewayResult.Failed(FailureCategory.PROVIDER_UNAVAILABLE, "503");

    private final MutableClock clock = new MutableClock();
    private final CircuitBreakerPolicy breaker = new CircuitBreakerPolicy(3, Duration.ofSeconds(30), clock);

    @Test
    void opensAfterConsecutiveFailures_andStopsCalling_safelyForFallback() {
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 4; i++) {
            breaker.run(() -> {
                calls.incrementAndGet();
                return UNAVAILABLE;
            });
        }

        assertThat(calls).as("the 4th call never happened").hasValue(3);
        assertThat(breaker.state()).isEqualTo(State.OPEN);
        assertThat(breaker.run(() -> UNAVAILABLE)).isInstanceOfSatisfying(GatewayResult.Failed.class,
                f -> assertThat(f.category().safeToFallback()).isTrue());
    }

    @Test
    void halfOpensThenClosesOnASuccessfulTrial() {
        open();
        clock.advance(Duration.ofSeconds(31));
        assertThat(breaker.state()).isEqualTo(State.HALF_OPEN);

        breaker.run(() -> new GatewayResult.Authorized("ref"));

        assertThat(breaker.state()).isEqualTo(State.CLOSED);
    }

    @Test
    void aFailedTrialReopens() {
        open();
        clock.advance(Duration.ofSeconds(31));

        breaker.run(() -> UNAVAILABLE);

        assertThat(breaker.state()).isEqualTo(State.OPEN);
    }

    @Test
    void aDeclineProvesTheProviderIsUp() {
        breaker.run(() -> UNAVAILABLE);
        breaker.run(() -> UNAVAILABLE);
        breaker.run(() -> new GatewayResult.Declined(DeclineReason.GENERIC));
        breaker.run(() -> UNAVAILABLE);
        breaker.run(() -> UNAVAILABLE);

        assertThat(breaker.state()).isEqualTo(State.CLOSED);
    }

    private void open() {
        for (int i = 0; i < 3; i++) {
            breaker.run(() -> UNAVAILABLE);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
