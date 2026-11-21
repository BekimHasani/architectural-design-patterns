package com.designpatterns.simulators;

/** Failure modes a simulator can be told to produce. More are added as later levels need them. */
public enum Fault {
    NONE,
    /** Hard decline: card declined / refused. */
    DECLINE,
    /** The customer must authenticate (3-D Secure) or approve before the payment completes. */
    REQUIRES_ACTION,
    /** Provider fails before processing; no charge happens. */
    HTTP_500,
    /** Provider unavailable; no charge happens. */
    HTTP_503,
    /** Rate limited, with {@code Retry-After: 1}; no charge happens. */
    HTTP_429,
    /**
     * The provider PROCESSES the request (charges the card), then never answers.
     * From the client's side it looks like any timeout, and that is the danger.
     */
    TIMEOUT,
    /** The provider charges, then the connection is cut before any answer is sent. */
    DROP_AFTER_COMMIT,
    /** Accepted but not final ("processing" / "Received"). The result comes later: call {@code settle(...)}. */
    ASYNC;

    static Fault parse(String value) {
        return valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
