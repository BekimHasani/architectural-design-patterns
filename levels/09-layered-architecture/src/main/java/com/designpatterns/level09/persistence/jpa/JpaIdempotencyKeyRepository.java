package com.designpatterns.level09.persistence.jpa;

import com.designpatterns.level09.persistence.IdempotencyKeyRecord;
import com.designpatterns.level09.persistence.IdempotencyKeyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Optional;

/**
 * The PRIMARY KEY on {@code idem_key} does the hard part: of N concurrent INSERTs for one key, the
 * database lets exactly one succeed. No locks in Java, no race.
 */
@Repository
class JpaIdempotencyKeyRepository implements IdempotencyKeyRepository {

    private final SpringDataIdempotencyKeys keys;
    private final Clock clock;

    JpaIdempotencyKeyRepository(SpringDataIdempotencyKeys keys, Clock clock) {
        this.keys = keys;
        this.clock = clock;
    }

    @Override
    public boolean insertInProgress(String key, String fingerprint) {
        // FLOW A.3c: saveAndFlush sends the INSERT now (not at some later commit), so a duplicate key
        // fails HERE, inside this try, and we can answer "not first".
        try {
            keys.saveAndFlush(new IdempotencyKeyEntity(key, fingerprint, clock.instant()));
            return true;
        } catch (DataIntegrityViolationException alreadyThere) {
            return false;
        }
    }

    @Override
    public Optional<IdempotencyKeyRecord> find(String key) {
        return keys.findById(key).map(k -> new IdempotencyKeyRecord(key, k.fingerprint(),
                IdempotencyKeyEntity.COMPLETED.equals(k.state()), k.httpStatus(), k.responseBody()));
    }

    @Override
    public void complete(String key, int httpStatus, String responseBody) {
        keys.complete(key, httpStatus, responseBody);
    }

    @Override
    public void release(String key) {
        keys.release(key);
    }
}
