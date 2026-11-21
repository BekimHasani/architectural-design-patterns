package com.designpatterns.level09.persistence.jpa;

import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.persistence.ProviderEventInboxRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Clock;

/** The primary key {@code (provider_id, event_id)} makes a duplicate delivery visible as a failed INSERT. */
@Repository
class JpaProviderEventInboxRepository implements ProviderEventInboxRepository {

    private final SpringDataInbox inbox;
    private final Clock clock;

    JpaProviderEventInboxRepository(SpringDataInbox inbox, Clock clock) {
        this.inbox = inbox;
        this.clock = clock;
    }

    @Override
    public boolean receive(ProviderId providerId, String eventId, String rawPayload) {
        // FLOW C.4: INSERT first, let the primary key detect the duplicate (no "check then insert" race)
        try {
            inbox.saveAndFlush(new ProviderEventInboxEntity(providerId.value(), eventId, rawPayload, clock.instant()));
            return true;
        } catch (DataIntegrityViolationException seenBefore) {
            // handled before → duplicate; stored but never finished (crash) → let this delivery finish it
            return inbox.findById(new ProviderEventInboxEntity.Key(providerId.value(), eventId))
                    .map(stored -> stored.outcome() == null)
                    .orElse(true);
        }
    }

    @Override
    public void markHandled(ProviderId providerId, String eventId, String outcome) {
        inbox.markHandled(providerId.value(), eventId, outcome);
    }
}
