package com.designpatterns.level02.service.idempotency;

/** The answer to "may I run this request?", for a client-supplied idempotency key. */
public sealed interface IdempotencyClaim {

    /** You are first: do the work, then call {@code complete}. */
    record Claimed() implements IdempotencyClaim {
    }

    /** Someone is doing it right now (double click). */
    record InProgress() implements IdempotencyClaim {
    }

    /** It was done before: here is the original answer. */
    record Completed(int httpStatus, String responseBody) implements IdempotencyClaim {
    }

    /** The key was used before for a DIFFERENT request: a client bug. */
    record Mismatch() implements IdempotencyClaim {
    }
}
