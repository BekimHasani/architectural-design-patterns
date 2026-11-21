package com.designpatterns.level01.payments.domain;

/** What {@link Payment#report} did with a piece of provider news. */
public enum ReportResult {
    APPLIED,
    ALREADY_KNOWN,
    /** Older than what we know (out-of-order delivery). Ignored. */
    STALE,
    /** Contradicts a final state. Must be surfaced to a human, never silently ignored. */
    CONFLICT
}
