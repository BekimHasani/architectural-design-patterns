package com.designpatterns.level08.payments.application.events;

/** What happened to one provider event. Stored in the inbox, so "what did we do with event X?" has an answer. */
public enum EventOutcome {
    APPLIED,
    /** The same event id was handled before. */
    DUPLICATE,
    /** Same news as we already had (e.g. the API answer said "succeeded" first). */
    ALREADY_KNOWN,
    /** Older than the payment's state: out-of-order delivery, ignored. */
    STALE_IGNORED,
    /** Contradicts a final state. Needs a human. */
    CONFLICT,
    /** We have no payment for this attempt id. */
    UNMATCHED,
    /** Not about payment state. */
    IGNORED
}
