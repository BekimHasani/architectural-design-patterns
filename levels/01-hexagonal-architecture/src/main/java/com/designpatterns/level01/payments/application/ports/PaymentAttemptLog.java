package com.designpatterns.level01.payments.application.ports;

import com.designpatterns.level01.payments.domain.PaymentId;
import com.designpatterns.level01.payments.domain.PaymentAttempt;

import java.util.List;
import java.util.Optional;

/** Port: durable record of every attempt. {@code IN_FLIGHT} first, the outcome afterwards. */
public interface PaymentAttemptLog {

    /** Inserts a new attempt, or updates it (same {@code attemptId}) with its outcome. */
    void save(PaymentAttempt attempt);

    List<PaymentAttempt> forPayment(PaymentId paymentId);

    /** Providers echo OUR attempt id back; this finds which payment it belongs to. */
    Optional<PaymentAttempt> find(String attemptId);
}
