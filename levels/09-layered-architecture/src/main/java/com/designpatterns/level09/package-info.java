/**
 * <h2>Level 9: the payment platform as a LAYERED architecture. Start reading here.</h2>
 *
 * <p>The application lets merchants take payments through several payment providers (Stripe-like,
 * PayPal-like, Adyen-like, ...). It has the same behaviour as Level 8; only the way the code is
 * organised changed.
 *
 * <h3>The layers (each one may only call the layers BELOW it)</h3>
 * <pre>
 *   presentation   HTTP controllers, webhook endpoint, timer      "how the outside world reaches us"
 *        │
 *     service      use cases: authorize, capture, refund, ...      "what the business does"
 *        │
 *   ┌────┴─────┐
 * persistence  integration   database  |  payment providers         "the things we talk to"
 *   └────┬─────┘
 *      model       Payment, Money, ids, rules                       "the business words and rules"
 *
 *   config         builds and connects everything (composition root), may see every layer
 * </pre>
 * The test {@code architecture.LayeredArchitectureTest} fails the build when an import breaks these
 * arrows. That is why the rules stay true over time: they are checked, not just written down.
 *
 * <h3>The five flows (search the code for "FLOW A", "FLOW B", ... to follow one step by step)</h3>
 * <ul>
 *   <li><b>FLOW S</b>: startup. How Spring builds and wires all the objects.</li>
 *   <li><b>FLOW A</b>: {@code POST /payments}, authorize a payment (the main flow, read it first).</li>
 *   <li><b>FLOW B</b>: {@code POST /payments/{id}/capture|cancel|refund}, act on an existing payment.</li>
 *   <li><b>FLOW C</b>: {@code POST /webhooks/{provider}}, a provider tells us what happened.</li>
 *   <li><b>FLOW D</b>: reconciliation, settling payments whose outcome we don't know.</li>
 * </ul>
 *
 * <p>The full reading guide is {@code docs/CODE-GUIDE.md} in this module.
 */
package com.designpatterns.level09;
