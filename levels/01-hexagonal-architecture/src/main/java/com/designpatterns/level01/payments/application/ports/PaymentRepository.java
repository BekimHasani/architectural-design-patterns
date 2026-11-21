package com.designpatterns.level01.payments.application.ports;

import com.designpatterns.level01.payments.domain.Payment;
import com.designpatterns.level01.payments.domain.PaymentId;
import com.designpatterns.level01.payments.domain.PaymentStatus;

import java.util.List;
import java.util.Optional;

/** Port: durable payments. No transaction ever spans a provider call. */
public interface PaymentRepository {

    /**
     * In ONE transaction: the payment, its operations and its new events. Optimistic locking: if
     * someone else saved since this copy was loaded, nothing is written and
     * {@link ConcurrentUpdateException} is thrown.
     *
     * @return the payment as stored, with its new version and no pending events
     */
    Payment save(Payment payment);

    Optional<Payment> find(PaymentId paymentId);

    List<Payment> findByStatus(PaymentStatus status);

    /** Payments with a capture, cancel or refund whose outcome is unknown. */
    List<Payment> findWithUnknownOperations();
}
