package com.designpatterns.level08.payments.domain;

/**
 * A provider's refund rules. Capabilities aren't always yes or no: "refunds, but only once, and
 * only in full" is data the aggregate can enforce.
 *
 * @param maxRefunds null = no limit
 */
public record RefundTerms(boolean partialAllowed, Integer maxRefunds) {

    public static final RefundTerms ANY = new RefundTerms(true, null);
}
