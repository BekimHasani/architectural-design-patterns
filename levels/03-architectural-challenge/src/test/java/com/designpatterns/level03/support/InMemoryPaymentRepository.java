package com.designpatterns.level03.support;

import com.designpatterns.level03.model.Payment;
import com.designpatterns.level03.persistence.PaymentRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Test double: a map instead of a database. */
public final class InMemoryPaymentRepository implements PaymentRepository {

    public final Map<String, Payment> rows = new LinkedHashMap<>();

    @Override
    public Payment save(Payment payment) {
        rows.put(payment.id(), payment);
        return payment;
    }

    @Override
    public Optional<Payment> findById(String paymentId) {
        return Optional.ofNullable(rows.get(paymentId));
    }
}
