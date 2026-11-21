package com.designpatterns.level02.model;

public enum ReportResult {
    APPLIED,
    ALREADY_KNOWN,
    /** Older than what we know (out-of-order delivery). Ignored. */
    STALE,
    /** Contradicts a final state. Must be surfaced to a human, never silently ignored. */
    CONFLICT
}