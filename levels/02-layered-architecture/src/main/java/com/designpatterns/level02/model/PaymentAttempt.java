package com.designpatterns.level02.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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