package com.designpatterns.level02.persistence;

import com.designpatterns.level02.model.PaymentId;

import java.util.List;

/**
 * Read side of the append-only {@code payment_events} table. There is no way to update or delete
 * through it. The write side is {@link PaymentRepository#save}, in the same transaction as the
 * state change.
 */
public interface AuditEventRepository {

    /** Oldest first. */
    List<StoredPaymentEvent> forPayment(PaymentId paymentId);
}
