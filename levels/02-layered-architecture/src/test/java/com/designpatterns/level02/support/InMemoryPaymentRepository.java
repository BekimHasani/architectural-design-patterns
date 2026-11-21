package com.designpatterns.level02.support;

import com.designpatterns.level02.model.Payment;
import com.designpatterns.level02.model.PaymentEvent;
import com.designpatterns.level02.model.PaymentId;
import com.designpatterns.level02.model.PaymentOperation;
import com.designpatterns.level02.model.PaymentStatus;
import com.designpatterns.level02.model.exceptions.ConcurrentUpdateException;
import com.designpatterns.level02.persistence.PaymentRepository;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test double with the same optimistic-locking contract as {@code JpaPaymentRepository}. */
public final class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<PaymentId, Payment> payments = new HashMap<>();
    public final List<PaymentStatus> savedStatuses = new ArrayList<>();
    public final List<PaymentEvent> events = new ArrayList<>();

    @Override
    public synchronized Payment save(Payment p) {
        Payment current = payments.get(p.id());
        if ((current == null ? 0 : current.version()) != p.version()) {
            throw new ConcurrentUpdateException(p.id());
        }
        Payment saved = restore(p, p.version() + 1);
        payments.put(p.id(), saved);
        savedStatuses.add(p.status());
        events.addAll(p.newEvents());
        return restore(saved, saved.version()).actingAs(p.actor());
    }

    @Override
    public synchronized Optional<Payment> find(PaymentId id) {
        return Optional.ofNullable(payments.get(id)).map(p -> restore(p, p.version()));
    }

    @Override
    public synchronized List<Payment> findByStatus(PaymentStatus status) {
        return payments.values().stream().filter(p -> p.status() == status).map(p -> restore(p, p.version())).toList();
    }

    @Override
    public synchronized List<Payment> findWithUnknownOperations() {
        return payments.values().stream()
                .filter(p -> p.operations().stream().anyMatch(op -> op.status() == PaymentOperation.Status.UNKNOWN))
                .map(p -> restore(p, p.version())).toList();
    }

    private static Payment restore(Payment p, int version) {
        return Payment.restore(p.id(), p.merchantId(), p.customerId(), p.amount(), p.captureMode(), p.status(), p.providerId(),
                p.providerRef(), p.attemptId(), p.redirectUrl(), p.reason(), p.operations(), version);
    }
}
