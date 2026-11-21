package com.designpatterns.level08.platform.persistence;

import com.designpatterns.level08.payments.application.ports.IdempotencyStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Clock;

/**
 * The PRIMARY KEY on {@code idem_key} does the hard part: of N concurrent INSERTs for one key,
 * the database lets exactly one succeed. No locks in Java, no race.
 */
public final class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcIdempotencyStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Claim claim(String key, String fingerprint) {
        try {
            jdbc.sql("INSERT INTO idempotency_keys (idem_key, fingerprint, state, created_at) VALUES (?, ?, 'IN_PROGRESS', ?)")
                    .params(key, fingerprint, Timestamp.from(clock.instant()))
                    .update();
            return new Claim.Claimed();
        } catch (DuplicateKeyException alreadyThere) {
            return jdbc.sql("SELECT fingerprint, state, http_status, response_body FROM idempotency_keys WHERE idem_key = ?")
                    .param(key)
                    .query((rs, row) -> {
                        if (!rs.getString("fingerprint").equals(fingerprint)) {
                            return (Claim) new Claim.Mismatch();
                        }
                        return "COMPLETED".equals(rs.getString("state"))
                                ? new Claim.Completed(rs.getInt("http_status"), rs.getString("response_body"))
                                : new Claim.InProgress();
                    })
                    .optional()
                    .orElseGet(Claim.InProgress::new); // released between our INSERT and SELECT: treat as busy
        }
    }

    @Override
    public void complete(String key, int httpStatus, String responseBody) {
        jdbc.sql("UPDATE idempotency_keys SET state = 'COMPLETED', http_status = ?, response_body = ? WHERE idem_key = ?")
                .params(httpStatus, responseBody, key)
                .update();
    }

    @Override
    public void release(String key) {
        jdbc.sql("DELETE FROM idempotency_keys WHERE idem_key = ? AND state = 'IN_PROGRESS'").param(key).update();
    }
}
