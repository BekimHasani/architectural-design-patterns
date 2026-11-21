package com.designpatterns.level09.service.idempotency;

import com.designpatterns.level09.persistence.IdempotencyKeyRecord;
import com.designpatterns.level09.persistence.IdempotencyKeyRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyServiceTest {

    /** A FAKE: a working, in-memory implementation of the persistence interface. */
    private static final class InMemoryKeys implements IdempotencyKeyRepository {
        final Map<String, IdempotencyKeyRecord> rows = new HashMap<>();

        @Override
        public synchronized boolean insertInProgress(String key, String fingerprint) {
            return rows.putIfAbsent(key, new IdempotencyKeyRecord(key, fingerprint, false, null, null)) == null;
        }

        @Override
        public Optional<IdempotencyKeyRecord> find(String key) {
            return Optional.ofNullable(rows.get(key));
        }

        @Override
        public void complete(String key, int httpStatus, String responseBody) {
            rows.computeIfPresent(key, (k, r) -> new IdempotencyKeyRecord(k, r.fingerprint(), true, httpStatus, responseBody));
        }

        @Override
        public void release(String key) {
            rows.computeIfPresent(key, (k, r) -> r.completed() ? r : null);
        }
    }

    private final InMemoryKeys keys = new InMemoryKeys();
    private final IdempotencyService service = new IdempotencyService(keys);

    @Test
    void theFirstClaimWins_aSecondOneWaits() {
        assertThat(service.claim("k", "hash")).isInstanceOf(IdempotencyClaim.Claimed.class);
        assertThat(service.claim("k", "hash")).isInstanceOf(IdempotencyClaim.InProgress.class);
    }

    @Test
    void aCompletedKeyReplaysTheOriginalAnswer() {
        service.claim("k", "hash");
        service.complete("k", 201, "{\"id\":1}");

        assertThat(service.claim("k", "hash")).isEqualTo(new IdempotencyClaim.Completed(201, "{\"id\":1}"));
    }

    @Test
    void theSameKeyForADifferentRequestIsAClientBug() {
        service.claim("k", "hash-1");

        assertThat(service.claim("k", "hash-2")).isInstanceOf(IdempotencyClaim.Mismatch.class);
    }

    @Test
    void aReleasedKeyCanBeClaimedAgain_butACompletedOneCannot() {
        service.claim("failed", "hash");
        service.release("failed");
        assertThat(service.claim("failed", "hash")).isInstanceOf(IdempotencyClaim.Claimed.class);

        service.claim("done", "hash");
        service.complete("done", 200, "{}");
        service.release("done");
        assertThat(service.claim("done", "hash")).isInstanceOf(IdempotencyClaim.Completed.class);
    }
}
