package com.designpatterns.level08.platform.persistence;

import com.designpatterns.level08.payments.application.ports.ProviderEventInbox;
import com.designpatterns.level08.payments.domain.ProviderId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;

/** The PRIMARY KEY {@code (provider_id, event_id)} makes a duplicate delivery visible as a failed INSERT. */
public final class JdbcProviderEventInbox implements ProviderEventInbox {

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcProviderEventInbox(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public boolean receive(ProviderId providerId, String eventId, String rawPayload) {
        try {
            jdbc.sql("INSERT INTO provider_event_inbox (provider_id, event_id, payload, received_at) VALUES (?, ?, ?, ?)")
                    .params(providerId.value(), eventId, rawPayload, Timestamp.from(clock.instant()))
                    .update();
            return true;
        } catch (DuplicateKeyException seenBefore) {
            // handled before → duplicate; stored but never finished (crash) → let this delivery finish it
            List<String> outcomes = jdbc.sql("SELECT outcome FROM provider_event_inbox WHERE provider_id = ? AND event_id = ?")
                    .params(providerId.value(), eventId)
                    .query((rs, row) -> rs.getString("outcome"))
                    .list();
            return outcomes.isEmpty() || outcomes.get(0) == null;
        }
    }

    @Override
    public void markHandled(ProviderId providerId, String eventId, String outcome) {
        jdbc.sql("UPDATE provider_event_inbox SET outcome = ? WHERE provider_id = ? AND event_id = ?")
                .params(outcome, providerId.value(), eventId)
                .update();
    }
}
