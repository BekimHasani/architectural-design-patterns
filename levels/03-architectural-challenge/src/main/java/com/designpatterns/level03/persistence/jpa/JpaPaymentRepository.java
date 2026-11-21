package com.designpatterns.level03.persistence.jpa;

import com.designpatterns.level03.model.Money;
import com.designpatterns.level03.model.Payment;
import com.designpatterns.level03.model.PaymentStatus;
import com.designpatterns.level03.persistence.PaymentRepository;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/** ADAPTER: {@link PaymentRepository} on Spring Data JPA. Maps entity ⇄ model. */
@Repository
public class JpaPaymentRepository implements PaymentRepository {

    private final SpringDataPayments rows;

    JpaPaymentRepository(SpringDataPayments rows) {
        this.rows = rows;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentEntity row = new PaymentEntity();
        row.id = payment.id();
        row.merchantId = payment.merchantId();
        row.customerId = payment.customerId();
        row.currency = payment.amount().currency().getCurrencyCode();
        row.amountMinor = payment.amount().amountMinor();
        row.status = payment.status().name();
        row.providerId = payment.providerId();
        row.providerRef = payment.providerReference();
        row.refundedMinor = payment.refunded().amountMinor();
        row.failureReason = payment.failureReason();
        row.updatedAt = Instant.now();
        rows.save(row);
        return payment;
    }

    @Override
    public Optional<Payment> findById(String paymentId) {
        return rows.findById(paymentId).map(row -> Payment.restore(
                row.id,
                row.merchantId,
                row.customerId,
                Money.of(row.amountMinor, row.currency),
                PaymentStatus.valueOf(row.status),
                row.providerId,
                row.providerRef,
                Money.of(row.refundedMinor, row.currency),
                row.failureReason));
    }
}
