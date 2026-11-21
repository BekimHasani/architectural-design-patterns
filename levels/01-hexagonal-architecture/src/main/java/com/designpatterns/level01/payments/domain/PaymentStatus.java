package com.designpatterns.level01.payments.domain;

/**
 * The ONE field that says where a payment is. Amounts (captured, refunded) are not statuses:
 * they are derived from the payment's operations, so they can't contradict each other.
 *
 * <pre>
 * CREATED → PENDING                  an authorize attempt is in flight (stored first)
 * PENDING → AUTHORIZED               funds held; capture or cancel next
 * PENDING → CAPTURED                 a direct-charge provider: authorize and capture are one step
 * PENDING → ACTION_REQUIRED          the customer must act; startCompletion goes back to PENDING
 * PENDING → PROCESSING               accepted, result comes later
 * PENDING → UNKNOWN                  timeout / lost answer; only provider news leaves this state
 * PENDING → CREATED                  nothing charged: next provider may be tried
 * PENDING → DECLINED | FAILED
 * AUTHORIZED → CAPTURED | CANCELLED  through a capture / cancel operation
 * CAPTURED → REFUNDED                when everything captured has been refunded
 * DECLINED, FAILED, CANCELLED, REFUNDED: final.
 * </pre>
 */
public enum PaymentStatus {
    CREATED,
    PENDING,
    ACTION_REQUIRED,
    PROCESSING,
    UNKNOWN,
    AUTHORIZED,
    CAPTURED,
    CANCELLED,
    REFUNDED,
    DECLINED,
    FAILED
}
