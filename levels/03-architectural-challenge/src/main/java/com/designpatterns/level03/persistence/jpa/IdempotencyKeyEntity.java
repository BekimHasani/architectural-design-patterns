package com.designpatterns.level03.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
class IdempotencyKeyEntity {

    @Id
    @Column(name = "idem_key")
    String key;
    String result;
    @Column(name = "created_at")
    Instant createdAt;

    protected IdempotencyKeyEntity() {
    }

    IdempotencyKeyEntity(String key, String result, Instant createdAt) {
        this.key = key;
        this.result = result;
        this.createdAt = createdAt;
    }
}
