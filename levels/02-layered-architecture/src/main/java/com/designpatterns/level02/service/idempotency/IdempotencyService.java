package com.designpatterns.level02.service.idempotency;

import com.designpatterns.level02.persistence.IdempotencyKeyRecord;
import com.designpatterns.level02.persistence.IdempotencyKeyRepository;
import org.springframework.stereotype.Service;

/**
 * Client → API idempotency rules. The repository below only stores rows; deciding what an existing row
 * MEANS (replay, in progress, misuse) is business logic, so it lives here. The presentation layer sees
 * only {@link IdempotencyClaim}, a service type, and never the persistence record.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository keys;

    public IdempotencyService(IdempotencyKeyRepository keys) {
        this.keys = keys;
    }

    /**
     * FLOW A.3b: of N concurrent claims for one key, exactly one gets {@link IdempotencyClaim.Claimed}.
     * The trick: we don't "check then insert" (two threads could both see "free"). We INSERT first and
     * let the database's primary key pick the single winner; losers then read what is stored.
     */
    public IdempotencyClaim claim(String scopedKey, String requestFingerprint) {
        if (keys.insertInProgress(scopedKey, requestFingerprint)) {
            return new IdempotencyClaim.Claimed();
        }
        return keys.find(scopedKey)
                .map(stored -> interpret(stored, requestFingerprint))
                .orElseGet(IdempotencyClaim.InProgress::new); // released between our INSERT and SELECT: treat as busy
    }

    public void complete(String scopedKey, int httpStatus, String responseBody) {
        keys.complete(scopedKey, httpStatus, responseBody);
    }

    /** The work failed unexpectedly: forget the key so the client may retry. */
    public void release(String scopedKey) {
        keys.release(scopedKey);
    }

    private static IdempotencyClaim interpret(IdempotencyKeyRecord stored, String fingerprint) {
        if (!stored.fingerprint().equals(fingerprint)) {
            return new IdempotencyClaim.Mismatch();
        }
        return stored.completed()
                ? new IdempotencyClaim.Completed(stored.httpStatus(), stored.responseBody())
                : new IdempotencyClaim.InProgress();
    }
}
