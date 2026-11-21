package com.designpatterns.level02.support;

import com.designpatterns.level02.model.PaymentAttempt;
import com.designpatterns.level02.model.PaymentId;
import com.designpatterns.level02.persistence.PaymentAttemptRepository;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test double for the attempt log: same contract as the JPA one, no database. */
public final class InMemoryPaymentAttemptRepository implements PaymentAttemptRepository {

    private final Map<String, PaymentAttempt> attempts = new LinkedHashMap<>();

    @Override
    public synchronized void save(PaymentAttempt attempt) {
        attempts.put(attempt.attemptId(), attempt);
    }

    @Override
    public synchronized List<PaymentAttempt> forPayment(PaymentId paymentId) {
        return attempts.values().stream().filter(a -> a.paymentId().equals(paymentId)).toList();
    }

    @Override
    public synchronized Optional<PaymentAttempt> find(String attemptId) {
        return Optional.ofNullable(attempts.get(attemptId));
    }
}
