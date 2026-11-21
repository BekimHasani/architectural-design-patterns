/**
 * <h2>MODEL layer: the business words and rules. Pure Java.</h2>
 *
 * <p><b>Job:</b> say what a payment IS and which changes are allowed. It knows nothing about HTTP,
 * databases, Spring or providers, so it can't import any of them (ArchUnit checks it). That makes it
 * the easiest code to test and the slowest to change.
 *
 * <p><b>Patterns you will find here:</b>
 * <ul>
 *   <li><b>Value Object</b> ({@link com.designpatterns.level09.model.Money}, {@code PaymentId},
 *       {@code MerchantId}, {@code ProviderId}): small immutable records compared by value. {@code Money}
 *       uses whole minor units ({@code 1999} = 19.99 EUR), never {@code double}, because floats can't
 *       represent cents exactly.</li>
 *   <li><b>Aggregate / Aggregate Root</b> ({@link com.designpatterns.level09.model.Payment}): the payment and
 *       its operations change together, and only through methods of {@code Payment}. Nobody can set a
 *       field from outside, so the rules can't be skipped.</li>
 *   <li><b>State machine</b>: {@code Payment} only allows certain status changes (CREATED → AUTHORIZED →
 *       CAPTURED ...). A wrong one throws {@code IllegalPaymentTransitionException}.</li>
 *   <li><b>Static Factory Methods</b>: {@code Payment.create(...)} starts a new payment (checks rules,
 *       records an event); {@code Payment.restore(...)} rebuilds a stored one (no checks, no events).
 *       Two names for two different intentions, better than two constructors.</li>
 *   <li><b>Domain Events</b> ({@code PaymentEvent}): every change records an event; persistence saves them
 *       as the audit trail in the same transaction.</li>
 *   <li><b>Tell, Don't Ask</b>: services say {@code payment.authorized(ref)}; they never do
 *       {@code if (payment.status() == X) payment.setStatus(Y)}.</li>
 * </ul>
 *
 * <p><b>Why {@code ConcurrentUpdateException} lives here:</b> persistence throws it, services catch it and
 * presentation turns it into HTTP 409. The model is the only layer all three can see.
 *
 * <p><b>Why {@code Capability} lives here:</b> routing (service) and providers (integration) must use the
 * same words ("CAPTURE", "REFUND"). The interfaces behind the words are in
 * {@code integration.gateway.CapabilityBinding}, because the model may not reference upper layers.
 */
package com.designpatterns.level09.model;
