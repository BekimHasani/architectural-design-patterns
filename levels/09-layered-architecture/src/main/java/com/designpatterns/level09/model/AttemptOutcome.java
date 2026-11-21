package com.designpatterns.level09.model;

public enum AttemptOutcome {
    /** Recorded before the provider call. If it stays like this, the process died mid-call. */
    IN_FLIGHT,
    AUTHORIZED,
    CAPTURED,
    DECLINED,
    REQUIRES_ACTION,
    /** Accepted by the provider; the final result arrives later (webhook or reconciliation). */
    PROCESSING,
    /** Failed, and we know nothing was charged (so falling back was allowed). */
    FAILED_NOTHING_CHARGED,
    /** Failed because our request was wrong. Nothing charged, but no point trying elsewhere. */
    FAILED_INVALID_REQUEST,
    /** We don't know whether the customer was charged. Reconciliation resolves the payment. */
    OUTCOME_UNKNOWN
}
