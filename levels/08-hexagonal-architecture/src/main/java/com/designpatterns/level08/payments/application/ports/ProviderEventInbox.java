package com.designpatterns.level08.payments.application.ports;

import com.designpatterns.level08.payments.domain.ProviderId;

/**
 * INBOX: every incoming provider event is stored (raw) before it is acted on, unique by
 * {@code (providerId, eventId)}. That gives deduplication, and an audit of what arrived.
 */
public interface ProviderEventInbox {

    /**
     * Stores the event if it is new. Returns false only if it was already HANDLED. An event
     * that was stored but never finished (process died mid-way) is handed out again, so a
     * provider's retry can complete it.
     */
    boolean receive(ProviderId providerId, String eventId, String rawPayload);

    void markHandled(ProviderId providerId, String eventId, String outcome);
}
