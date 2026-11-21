package com.designpatterns.level08.audit;

import com.designpatterns.level08.payments.domain.PaymentId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

/**
 * THE AUDIT MODULE: reads the append-only {@code payment_events} table. There is no way to update
 * or delete through this module. The write side is the payment repository, in the same
 * transaction as the state change.
 */
public final class AuditTrail {

    private final JdbcClient jdbc;

    public AuditTrail(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<AuditEntry> forPayment(PaymentId paymentId) {
        return jdbc.sql("SELECT * FROM payment_events WHERE payment_id = ? ORDER BY seq").param(paymentId.value())
                .query((rs, row) -> {
                    long amount = rs.getLong("amount_minor");
                    Long amountMinor = rs.wasNull() ? null : amount;
                    return new AuditEntry(rs.getLong("seq"), rs.getString("type"), rs.getString("from_status"),
                            rs.getString("to_status"), amountMinor, rs.getString("actor"), rs.getString("detail"),
                            rs.getTimestamp("occurred_at").toInstant());
                })
                .list();
    }
}
