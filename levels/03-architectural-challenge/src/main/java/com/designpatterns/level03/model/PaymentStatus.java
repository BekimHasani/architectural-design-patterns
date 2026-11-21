package com.designpatterns.level03.model;

/**
 * The lifecycle of a payment.
 *
 * <pre>
 * CREATED ──► CAPTURED ──► PARTIALLY_REFUNDED ──► REFUNDED
 *    │
 *    ├──► DECLINED          the provider said no
 *    ├──► FAILED            no provider could process it, and nothing was charged
 *    └──► OUTCOME_UNKNOWN   a provider may have charged, but we never got the answer
 * </pre>
 */
public enum PaymentStatus {
    CREATED,
    CAPTURED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    DECLINED,
    FAILED,
    OUTCOME_UNKNOWN;

    public boolean isRefundable() {
        return this == CAPTURED || this == PARTIALLY_REFUNDED;
    }
}
