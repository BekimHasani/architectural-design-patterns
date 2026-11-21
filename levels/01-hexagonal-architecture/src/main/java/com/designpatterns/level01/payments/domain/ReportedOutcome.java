package com.designpatterns.level01.payments.domain;

/**
 * What a provider told us about a payment, in our words. Nothing here says how it reached us
 * (webhook, API answer, polling): the domain doesn't care.
 */
public enum ReportedOutcome {
    PROCESSING,
    /** Funds are held. */
    AUTHORIZED,
    /** Money moved: a direct charge. */
    CAPTURED,
    DECLINED
}
