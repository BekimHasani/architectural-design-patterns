package com.designpatterns.level01.platform.persistence;

import com.designpatterns.level01.payments.application.ports.PaymentAttemptLog;
import com.designpatterns.level01.payments.domain.AttemptOutcome;
import com.designpatterns.level01.payments.domain.PaymentId;
import com.designpatterns.level01.payments.domain.PaymentAttempt;
import com.designpatterns.level01.payments.domain.ProviderId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public final class JdbcPaymentAttemptLog implements PaymentAttemptLog {

    private final JdbcClient jdbc;

    public JdbcPaymentAttemptLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(PaymentAttempt a) {
        int updated = jdbc.sql("UPDATE payment_attempts SET duration_ms = ?, outcome = ? WHERE attempt_id = ?")
                .params(a.duration().toMillis(), a.outcome().name(), a.attemptId())
                .update();
        if (updated == 0) {
            jdbc.sql("""
                            INSERT INTO payment_attempts (attempt_id, payment_id, provider_id, started_at, duration_ms, outcome)
                            VALUES (?, ?, ?, ?, ?, ?)""")
                    .params(a.attemptId(), a.paymentId().value(), a.providerId().value(), Timestamp.from(a.startedAt()),
                            a.duration().toMillis(), a.outcome().name())
                    .update();
        }
    }

    @Override
    public List<PaymentAttempt> forPayment(PaymentId paymentId) {
        return jdbc.sql("SELECT * FROM payment_attempts WHERE payment_id = ? ORDER BY started_at, attempt_id")
                .param(paymentId.value())
                .query(JdbcPaymentAttemptLog::map)
                .list();
    }

    @Override
    public Optional<PaymentAttempt> find(String attemptId) {
        return jdbc.sql("SELECT * FROM payment_attempts WHERE attempt_id = ?").param(attemptId)
                .query(JdbcPaymentAttemptLog::map).optional();
    }

    private static PaymentAttempt map(ResultSet rs, int row) throws SQLException {
        return new PaymentAttempt(rs.getString("attempt_id"), new PaymentId(rs.getString("payment_id")),
                ProviderId.of(rs.getString("provider_id")), rs.getTimestamp("started_at").toInstant(),
                Duration.ofMillis(rs.getLong("duration_ms")), AttemptOutcome.valueOf(rs.getString("outcome")));
    }
}
