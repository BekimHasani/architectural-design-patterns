package com.designpatterns.level09.support;

import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.persistence.ProviderEventInboxRepository;


import java.util.LinkedHashMap;
import java.util.Map;

/** Test double with the same contract as the JPA inbox. */
public final class InMemoryProviderEventInboxRepository implements ProviderEventInboxRepository {

    /** key → outcome (null while not handled) */
    public final Map<String, String> events = new LinkedHashMap<>();

    @Override
    public synchronized boolean receive(ProviderId providerId, String eventId, String rawPayload) {
        String key = providerId + "/" + eventId;
        if (events.containsKey(key) && events.get(key) != null) {
            return false;
        }
        events.put(key, null);
        return true;
    }

    @Override
    public synchronized void markHandled(ProviderId providerId, String eventId, String outcome) {
        events.put(providerId + "/" + eventId, outcome);
    }
}
