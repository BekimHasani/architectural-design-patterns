package com.designpatterns.level08.payments.domain;

/**
 * A fact about a payment: WHAT changed, WHO did it, WHY. The aggregate produces these itself, so
 * no state change can happen without one; the repository stores them with the state change.
 *
 * @param amountMinor null if the event moved no money
 * @param actor       e.g. {@code merchant:acme}, {@code provider:stripe}, {@code reconciliation}
 */
public record PaymentEvent(String type, PaymentStatus from, PaymentStatus to, Long amountMinor, String actor,
                           String detail) {
}
