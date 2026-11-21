package com.designpatterns.level01.payments.application.ports;

/**
 * Port for the INBOUND idempotency boundary (client → our API). The store must make
 * {@link #claim} atomic: of N concurrent claims for one key, exactly one gets {@code Claimed}.
 */
public interface IdempotencyStore {

    sealed interface Claim {
        /** You are first: do the work, then call {@link #complete}. */
        record Claimed() implements Claim {
        }

        /** Someone is doing it right now (double click). */
        record InProgress() implements Claim {
        }

        /** It was done before: here is the original answer. */
        record Completed(int httpStatus, String responseBody) implements Claim {
        }

        /** The key was used before for a DIFFERENT request: a client bug. */
        record Mismatch() implements Claim {
        }
    }

    Claim claim(String key, String requestFingerprint);

    void complete(String key, int httpStatus, String responseBody);

    /** The work failed unexpectedly: forget the key so the client may retry. */
    void release(String key);
}
