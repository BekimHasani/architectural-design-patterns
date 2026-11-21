package com.designpatterns.level09.persistence.jpa;

import com.designpatterns.level09.model.CaptureMode;
import com.designpatterns.level09.model.MerchantId;
import com.designpatterns.level09.model.Money;
import com.designpatterns.level09.model.Payment;
import com.designpatterns.level09.model.PaymentEvent;
import com.designpatterns.level09.model.PaymentId;
import com.designpatterns.level09.model.PaymentOperation;
import com.designpatterns.level09.model.PaymentStatus;
import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.model.exceptions.ConcurrentUpdateException;
import com.designpatterns.level09.persistence.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The aggregate in JPA (ADAPTER + DATA MAPPER). {@link #save} writes payment, operations and new
 * events in ONE transaction: a state change can't exist without its audit event, or the other way
 * round. Loading reads the payment and its operations from ONE snapshot: a current version with an
 * older list of operations would pass the version check while missing a reservation.
 *
 * <p>Transactions are opened HERE, with a {@link TransactionTemplate}, and never in the service layer:
 * a service method spans provider calls, and no database transaction may stay open during one.
 */
@Repository
class JpaPaymentRepository implements PaymentRepository {

    private final SpringDataPayments payments;
    private final SpringDataOperations operations;
    private final SpringDataEvents events;
    private final TransactionTemplate transaction;
    private final TransactionTemplate snapshot;
    private final Clock clock;

    JpaPaymentRepository(SpringDataPayments payments, SpringDataOperations operations, SpringDataEvents events,
                         PlatformTransactionManager transactions, Clock clock) {
        this.payments = payments;
        this.operations = operations;
        this.events = events;
        this.transaction = new TransactionTemplate(transactions);
        this.snapshot = new TransactionTemplate(transactions);
        this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.snapshot.setReadOnly(true);
        this.clock = clock;
    }

    /**
     * FLOW A.8, A.9, A.12, B.4, B.6, C.7, D: every change of a payment ends here.
     * One {@code TransactionTemplate.executeWithoutResult}: everything inside commits together, or not at all.
     */
    @Override
    public Payment save(Payment p) {
        try {
            transaction.executeWithoutResult(tx -> {
                Instant now = clock.instant();
                writePayment(p, now);
                writeOperations(p);
                writeEvents(p, now);
            });
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException lostTheRace) {
            // EXCEPTION TRANSLATION: database words → a business word from the model layer
            // same id inserted twice, or the row stayed locked by another writer for too long
            throw new ConcurrentUpdateException(p.id());
        }
        // a fresh copy: new version, no pending events (they are stored now)
        return Payment.restore(p.id(), p.merchantId(), p.customerId(), p.amount(), p.captureMode(), p.status(),
                        p.providerId(), p.providerRef(), p.attemptId(), p.redirectUrl(), p.reason(), p.operations(),
                        p.version() + 1)
                .actingAs(p.actor());
    }

    /**
     * Optimistic check, made safe with a short row lock: lock the row, compare versions, write. Without
     * the lock, two transactions could both read version 3 and both write version 4 (a lost update).
     */
    private void writePayment(Payment p, Instant now) {
        PaymentEntity row;
        if (p.version() == 0) {                             // never stored: build a new row (INSERT)
            row = new PaymentEntity(p.id().value(), p.merchantId().value(), p.customerId(),
                    p.amount().currency().getCurrencyCode(), p.amount().amountMinor(), p.captureMode().name());
        } else {
            row = payments.findForUpdate(p.id().value())   // SELECT ... FOR UPDATE: others wait here
                    .filter(stored -> stored.version() == p.version()) // changed since we loaded? → we lost
                    .orElseThrow(() -> new ConcurrentUpdateException(p.id()));
        }
        row.update(p.status().name(), p.providerId() == null ? null : p.providerId().value(), p.providerRef(),
                p.attemptId(), p.redirectUrl() == null ? null : p.redirectUrl().toString(), p.reason(),
                p.version() + 1, now);
        payments.saveAndFlush(row); // a new row is a real INSERT (Persistable): a duplicate id fails here
    }

    private void writeOperations(Payment p) {
        int position = 0;
        for (PaymentOperation op : p.operations()) {
            int at = position++;
            PaymentOperationEntity row = operations.findById(op.operationId())
                    .orElseGet(() -> new PaymentOperationEntity(op.operationId(), p.id().value(), op.type().name(),
                            op.amount() == null ? null : op.amount().amountMinor(), at));
            row.update(op.status().name(), op.providerRef(), op.reason());
            operations.save(row);
        }
    }

    private void writeEvents(Payment p, Instant now) {
        for (PaymentEvent e : p.newEvents()) {
            events.save(new PaymentEventEntity(p.id().value(), e.type(), e.from().name(), e.to().name(),
                    e.amountMinor(), e.actor(), e.detail(), now));
        }
    }

    @Override
    public Optional<Payment> find(PaymentId paymentId) {
        return snapshot.execute(tx -> payments.findById(paymentId.value()).map(this::toModel));
    }

    @Override
    public List<Payment> findByStatus(PaymentStatus status) {
        return snapshot.execute(tx -> payments.findByStatusOrderByUpdatedAt(status.name()).stream()
                .map(this::toModel).toList());
    }

    @Override
    public List<Payment> findWithUnknownOperations() {
        return snapshot.execute(tx -> payments.findWithUnknownOperations().stream().map(this::toModel).toList());
    }

    /** DATA MAPPER, row → aggregate: strings become value objects and enums; {@code restore} skips the rules. */
    private Payment toModel(PaymentEntity row) {
        String currency = row.currency();
        List<PaymentOperation> ops = operations.findByPaymentIdOrderByPosition(row.getId()).stream()
                .map(op -> new PaymentOperation(op.operationId(), PaymentOperation.Type.valueOf(op.type()),
                        op.amountMinor() == null ? null : Money.of(op.amountMinor(), currency),
                        PaymentOperation.Status.valueOf(op.status()), op.providerRef(), op.reason()))
                .toList();
        return Payment.restore(new PaymentId(row.getId()), new MerchantId(row.merchantId()), row.customerId(),
                Money.of(row.amountMinor(), currency), CaptureMode.valueOf(row.captureMode()),
                PaymentStatus.valueOf(row.status()),
                row.providerId() == null ? null : ProviderId.of(row.providerId()), row.providerRef(), row.attemptId(),
                row.redirectUrl() == null ? null : URI.create(row.redirectUrl()), row.reason(), ops, row.version());
    }
}
