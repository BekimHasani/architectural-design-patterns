/**
 * <h2>One folder per payment provider. Read {@code stripe} first: it supports everything.</h2>
 *
 * <p>Inside a provider folder (example: {@code stripe}):
 * <pre>
 *   StripeFactory          {@code @Component}, plug-in: "I can build the stripe adapter"     (FLOW S)
 *   StripeGateway          public ADAPTER: implements the capability interfaces           (FLOW A/B/D)
 *   StripeMapper           pure functions: our request → Stripe JSON, Stripe reply → GatewayResult
 *   StripeApi              Stripe's JSON shapes as records (package-private)
 *   webhook/               signature check + webhook mapping + translator                 (FLOW C)
 * </pre>
 * Together they form an <b>Anti-Corruption Layer</b>: Stripe's words ("payment_intent", "requires_capture")
 * stop here; the rest of the system only sees our words ("Authorized").
 *
 * <p>Only {@code *Gateway} is public. Everything else is package-private, so other code can't depend on a
 * provider's details. Providers may not import each other (ArchUnit).
 */
package com.designpatterns.level02.integration.provider;
