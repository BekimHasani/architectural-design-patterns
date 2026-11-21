package com.designpatterns.level02.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A {@code payment_events} row: append-only, so it has no setters at all. */
@Entity
@Table(name = "payment_events")
class PaymentEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seq;
    @Column(name = "payment_id")
    private String paymentId;
    private String type;
    @Column(name = "from_status")
    private String fromStatus;
    @Column(name = "to_status")
    private String toStatus;
    @Column(name = "amount_minor")
    private Long amountMinor;
    private String actor;
    private String detail;
    @Column(name = "occurred_at")
    private Instant occurredAt;

    protected PaymentEventEntity() {
    }

    PaymentEventEntity(String paymentId, String type, String fromStatus, String toStatus, Long amountMinor,
                       String actor, String detail, Instant occurredAt) {
        this.paymentId = paymentId;
        this.type = type;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.amountMinor = amountMinor;
        this.actor = actor;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    Long seq() { return seq; }
    String type() { return type; }
    String fromStatus() { return fromStatus; }
    String toStatus() { return toStatus; }
    Long amountMinor() { return amountMinor; }
    String actor() { return actor; }
    String detail() { return detail; }
    Instant occurredAt() { return occurredAt; }
}
