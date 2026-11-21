package com.designpatterns.level02.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/** An {@code idempotency_keys} row. INSERT-only via {@link Persistable}: a duplicate key must FAIL, not merge. */
@Entity
@Table(name = "idempotency_keys")
class IdempotencyKeyEntity implements Persistable<String> {

    static final String IN_PROGRESS = "IN_PROGRESS";
    static final String COMPLETED = "COMPLETED";

    @Id
    @Column(name = "idem_key")
    private String key;
    private String fingerprint;
    private String state;
    @Column(name = "http_status")
    private Integer httpStatus;
    @Lob
    @Column(name = "response_body")
    private String responseBody;
    @Column(name = "created_at")
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected IdempotencyKeyEntity() {
    }

    IdempotencyKeyEntity(String key, String fingerprint, Instant createdAt) {
        this.key = key;
        this.fingerprint = fingerprint;
        this.state = IN_PROGRESS;
        this.createdAt = createdAt;
    }

    @Override
    public String getId() {
        return key;
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

    String fingerprint() { return fingerprint; }
    String state() { return state; }
    Integer httpStatus() { return httpStatus; }
    String responseBody() { return responseBody; }
}
