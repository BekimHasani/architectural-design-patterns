package com.designpatterns.level09.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;

/** A {@code provider_event_inbox} row, keyed by (provider, event id). INSERT-only via {@link Persistable}. */
@Entity
@Table(name = "provider_event_inbox")
@IdClass(ProviderEventInboxEntity.Key.class)
class ProviderEventInboxEntity implements Persistable<ProviderEventInboxEntity.Key> {

    /** The composite primary key. JPA requires it to be Serializable with equals/hashCode: a record gives both. */
    record Key(String providerId, String eventId) implements Serializable {
    }

    @Id
    @Column(name = "provider_id")
    private String providerId;
    @Id
    @Column(name = "event_id")
    private String eventId;
    @Lob
    private String payload;
    @Column(name = "received_at")
    private Instant receivedAt;
    private String outcome;

    @Transient
    private boolean isNew = true;

    protected ProviderEventInboxEntity() {
    }

    ProviderEventInboxEntity(String providerId, String eventId, String payload, Instant receivedAt) {
        this.providerId = providerId;
        this.eventId = eventId;
        this.payload = payload;
        this.receivedAt = receivedAt;
    }

    @Override
    public Key getId() {
        return new Key(providerId, eventId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    String outcome() {
        return outcome;
    }
}
