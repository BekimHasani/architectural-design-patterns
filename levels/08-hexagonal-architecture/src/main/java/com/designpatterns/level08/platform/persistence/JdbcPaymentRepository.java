package com.designpatterns.level08.platform.persistence;

import com.designpatterns.level08.payments.application.ports.ConcurrentUpdateException;
import com.designpatterns.level08.payments.application.ports.PaymentRepository;
import com.designpatterns.level08.payments.domain.CaptureMode;
import com.designpatterns.level08.payments.domain.MerchantId;
import com.designpatterns.level08.payments.domain.Money;
import com.designpatterns.level08.payments.domain.Payment;
import com.designpatterns.level08.payments.domain.PaymentEvent;
import com.designpatterns.level08.payments.domain.PaymentId;
import com.designpatterns.level08.payments.domain.PaymentOperation;
import com.designpatterns.level08.payments.domain.PaymentStatus;
import com.designpatterns.level08.payments.domain.ProviderId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The aggregate in SQL. {@link #save} writes payment, operations and new events in ONE transaction:
 * a state change can't exist without its audit event, or the other way round. Loading reads the
 * payment and its operations from ONE snapshot: a current version with an older list of operations
 * would pass the version check while missing a reservation.
 */
public final class JdbcPaymentRepository implements PaymentRepository {

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final TransactionTemplate snapshot;
    private final Clock clock;

    public JdbcPaymentRepository(JdbcClient jdbc, TransactionTemplate transaction, Clock clock) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.snapshot = new TransactionTemplate(transaction.getTransactionManager());
        this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.snapshot.setReadOnly(true);
        this.clock = clock;
    }

    @Override
    public Payment save(Payment p) {
        transaction.executeWithoutResult(tx -> {
            writePayment(p);
            writeOperations(p);
            writeEvents(p);
        });
        return Payment.restore(p.id(), p.merchantId(), p.customerId(), p.amount(), p.captureMode(), p.status(), p.providerId(),
                p.providerRef(), p.attemptId(), p.redirectUrl(), p.reason(), p.operations(), p.version() + 1)
                .actingAs(p.actor());
    }

    private void writePayment(Payment p) {
        int rows;
        if (p.version() == 0) {
            try {
                rows = jdbc.sql("""
                                INSERT INTO payments (id, merchant_id, customer_id, currency, amount_minor, capture_mode, status, provider_id,
                                    provider_ref, attempt_id, redirect_url, reason, version, updated_at)
                                VALUES (:id, :merchant, :customer, :currency, :amount, :mode, :status, :provider, :ref, :attempt,
                                    :redirect, :reason, 1, :now)""")
                        .params(params(p))
                        .update();
            } catch (DuplicateKeyException e) {
                throw new ConcurrentUpdateException(p.id());
            }
        } else {
            rows = jdbc.sql("""
                            UPDATE payments SET status = :status, provider_id = :provider, provider_ref = :ref,
                                attempt_id = :attempt, redirect_url = :redirect, reason = :reason,
                                version = version + 1, updated_at = :now
                            WHERE id = :id AND version = :version""")
                    .params(params(p))
                    .param("version", p.version())
                    .update();
        }
        if (rows != 1) {
            throw new ConcurrentUpdateException(p.id());
        }
    }

    private void writeOperations(Payment p) {
        int position = 0;
        for (PaymentOperation op : p.operations()) {
            Long amount = op.amount() == null ? null : op.amount().amountMinor();
            int updated = jdbc.sql("UPDATE payment_operations SET status = ?, provider_ref = ?, reason = ? WHERE operation_id = ?")
                    .params(op.status().name(), op.providerRef(), op.reason(), op.operationId())
                    .update();
            if (updated == 0) {
                jdbc.sql("""
                                INSERT INTO payment_operations (operation_id, payment_id, type, amount_minor, status, provider_ref, reason, position)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
                        .params(op.operationId(), p.id().value(), op.type().name(), amount, op.status().name(),
                                op.providerRef(), op.reason(), position)
                        .update();
            }
            position++;
        }
    }

    private void writeEvents(Payment p) {
        Timestamp now = Timestamp.from(clock.instant());
        for (PaymentEvent e : p.newEvents()) {
            jdbc.sql("""
                            INSERT INTO payment_events (payment_id, type, from_status, to_status, amount_minor, actor, detail, occurred_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(p.id().value(), e.type(), e.from().name(), e.to().name(), e.amountMinor(), e.actor(), e.detail(), now)
                    .update();
        }
    }

    @Override
    public Optional<Payment> find(PaymentId paymentId) {
        return snapshot.execute(tx -> jdbc.sql("SELECT * FROM payments WHERE id = ?").param(paymentId.value()).query(this::map).optional());
    }

    @Override
    public List<Payment> findByStatus(PaymentStatus status) {
        return snapshot.execute(tx -> jdbc.sql("SELECT * FROM payments WHERE status = ? ORDER BY updated_at").param(status.name()).query(this::map).list());
    }

    @Override
    public List<Payment> findWithUnknownOperations() {
        return snapshot.execute(tx -> jdbc.sql("""
                        SELECT * FROM payments WHERE id IN
                            (SELECT payment_id FROM payment_operations WHERE status = 'UNKNOWN') ORDER BY updated_at""")
                .query(this::map).list());
    }

    private Map<String, Object> params(Payment p) {
        Map<String, Object> m = new HashMap<>(); // HashMap, not Map.of: several values may be null
        m.put("id", p.id().value());
        m.put("merchant", p.merchantId().value());
        m.put("customer", p.customerId());
        m.put("currency", p.amount().currency().getCurrencyCode());
        m.put("amount", p.amount().amountMinor());
        m.put("mode", p.captureMode().name());
        m.put("status", p.status().name());
        m.put("provider", p.providerId() == null ? null : p.providerId().value());
        m.put("ref", p.providerRef());
        m.put("attempt", p.attemptId());
        m.put("redirect", p.redirectUrl() == null ? null : p.redirectUrl().toString());
        m.put("reason", p.reason());
        m.put("now", Timestamp.from(clock.instant()));
        return m;
    }

    private Payment map(ResultSet rs, int row) throws SQLException {
        String currency = rs.getString("currency");
        String id = rs.getString("id");
        String provider = rs.getString("provider_id");
        String redirect = rs.getString("redirect_url");
        return Payment.restore(new PaymentId(id), new MerchantId(rs.getString("merchant_id")), rs.getString("customer_id"),
                Money.of(rs.getLong("amount_minor"), currency), CaptureMode.valueOf(rs.getString("capture_mode")), PaymentStatus.valueOf(rs.getString("status")),
                provider == null ? null : ProviderId.of(provider), rs.getString("provider_ref"), rs.getString("attempt_id"),
                redirect == null ? null : URI.create(redirect), rs.getString("reason"), operations(id, currency),
                rs.getInt("version"));
    }

    private List<PaymentOperation> operations(String paymentId, String currency) {
        return jdbc.sql("SELECT * FROM payment_operations WHERE payment_id = ? ORDER BY position").param(paymentId)
                .query((rs, row) -> {
                    long amount = rs.getLong("amount_minor");
                    return new PaymentOperation(rs.getString("operation_id"),
                            PaymentOperation.Type.valueOf(rs.getString("type")),
                            rs.wasNull() ? null : Money.of(amount, currency),
                            PaymentOperation.Status.valueOf(rs.getString("status")),
                            rs.getString("provider_ref"), rs.getString("reason"));
                })
                .list();
    }
}
