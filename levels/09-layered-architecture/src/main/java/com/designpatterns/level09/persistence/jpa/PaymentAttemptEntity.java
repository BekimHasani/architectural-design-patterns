package com.designpatterns.level09.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A {@code payment_attempts} row. Saved twice: IN_FLIGHT before the call, with its outcome after. */
@Entity
@Table(name = "payment_attempts")
class PaymentAttemptEntity {

    @Id
    @Column(name = "attempt_id")
    private String attemptId;
    @Column(name = "payment_id")
    private String paymentId;
    @Column(name = "provider_id")
    private String providerId;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "duration_ms")
    private long durationMs;
    private String outcome;

    protected PaymentAttemptEntity() {
    }

    PaymentAttemptEntity(String attemptId, String paymentId, String providerId, Instant startedAt, long durationMs,
                         String outcome) {
        this.attemptId = attemptId;
        this.paymentId = paymentId;
        this.providerId = providerId;
        this.startedAt = startedAt;
        this.durationMs = durationMs;
        this.outcome = outcome;
    }

    String attemptId() { return attemptId; }
    String paymentId() { return paymentId; }
    String providerId() { return providerId; }
    Instant startedAt() { return startedAt; }
    long durationMs() { return durationMs; }
    String outcome() { return outcome; }
}
