package com.designpatterns.level09.persistence;

import com.designpatterns.level09.model.PaymentAttempt;
import com.designpatterns.level09.model.PaymentId;

import java.util.List;
import java.util.Optional;

/** Durable record of every attempt: {@code IN_FLIGHT} first, the outcome afterwards. */
public interface PaymentAttemptRepository {

    /** Inserts a new attempt, or updates it (same {@code attemptId}) with its outcome. */
    void save(PaymentAttempt attempt);

    List<PaymentAttempt> forPayment(PaymentId paymentId);

    /** Providers echo OUR attempt id back; this finds which payment it belongs to. */
    Optional<PaymentAttempt> find(String attemptId);
}
