package com.designpatterns.level03.persistence;

import com.designpatterns.level03.model.Payment;

import java.util.Optional;

public interface PaymentRepository {

    /** Inserts or updates. Returns the payment it was given. */
    Payment save(Payment payment);

    Optional<Payment> findById(String paymentId);
}
