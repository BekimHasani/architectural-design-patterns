package com.designpatterns.level09.persistence;

import com.designpatterns.level09.model.Payment;
import com.designpatterns.level09.model.PaymentId;
import com.designpatterns.level09.model.PaymentStatus;
import com.designpatterns.level09.model.exceptions.ConcurrentUpdateException;

import java.util.List;
import java.util.Optional;

/**
 * REPOSITORY for the {@link Payment} aggregate: a collection-like view of stored payments.
 *
 * <p>LAYERED CHANGE: this interface is OWNED by the persistence layer (it lives next to its JPA
 * implementation), and the service layer depends on it downwards. In Level 8 the core owned it as a
 * port. We still keep it as an interface so service tests can use an in-memory fake.
 *
 * <p>It speaks in MODEL types, never in JPA entities: the {@code @Entity} classes stay package-private
 * in {@code persistence.jpa}, so no layer above can couple to the table layout.
 */
public interface PaymentRepository {

    /**
     * In ONE short transaction: the payment, its operations and its new events. Optimistic check: if
     * someone else saved since this copy was loaded, nothing is written and
     * {@link ConcurrentUpdateException} is thrown. Never call it inside a transaction that spans a
     * provider call.
     *
     * @return the payment as stored, with its new version and no pending events
     */
    Payment save(Payment payment);

    Optional<Payment> find(PaymentId paymentId);

    List<Payment> findByStatus(PaymentStatus status);

    /** Payments with a capture, cancel or refund whose outcome is unknown. */
    List<Payment> findWithUnknownOperations();
}
