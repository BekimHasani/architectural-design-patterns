package com.designpatterns.level02.persistence;

import java.util.Optional;

/**
 * Storage for client → API idempotency keys. Deliberately DUMB: it inserts, reads, updates and
 * deletes rows. Deciding what a stored key means (replay? in progress? misuse?) is business logic
 * and lives in {@code service.idempotency.IdempotencyService}.
 */
public interface IdempotencyKeyRepository {

    /**
     * Atomic "insert if absent": of N concurrent calls for one key, exactly one returns true. The
     * database's primary key does the hard part, not a Java lock.
     */
    boolean insertInProgress(String key, String fingerprint);

    Optional<IdempotencyKeyRecord> find(String key);

    void complete(String key, int httpStatus, String responseBody);

    /** Deletes the key, but only while it is still in progress. */
    void release(String key);
}
