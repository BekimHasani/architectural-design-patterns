package com.designpatterns.level08.payments.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One try at one provider for one order. Recorded as {@code IN_FLIGHT} BEFORE the provider
 * call, then updated with the outcome. {@code attemptId} is STABLE: it is the idempotency
 * key and reference we send to the provider, so a retry of this attempt can never charge twice.
 */
public record PaymentAttempt(String attemptId, PaymentId paymentId, ProviderId providerId, Instant startedAt,
                             Duration duration, AttemptOutcome outcome) {

    public PaymentAttempt {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(outcome, "outcome");
    }

    public static PaymentAttempt start(PaymentId paymentId, ProviderId providerId, Instant now) {
        return new PaymentAttempt("att_" + UUID.randomUUID(), paymentId, providerId, now, Duration.ZERO,
                AttemptOutcome.IN_FLIGHT);
    }

    public PaymentAttempt finish(AttemptOutcome result, Instant now) {
        return new PaymentAttempt(attemptId, paymentId, providerId, startedAt, Duration.between(startedAt, now), result);
    }
}
