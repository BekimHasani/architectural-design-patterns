/**
 * <h2>Routing: which provider(s) may take this payment? (FLOW A, step A.6)</h2>
 *
 * <ul>
 *   <li><b>Strategy</b>: each {@link com.designpatterns.level02.service.routing.RoutingRule} is one way to
 *       propose providers (by merchant, by currency, default).</li>
 *   <li><b>Chain of Responsibility</b>, in a list: rules are asked in order; their proposals are joined into
 *       one ordered, de-duplicated list. The first candidate is tried first, the rest are fallbacks.</li>
 *   <li><b>Filter then choose</b>: a proposed provider that can't take the currency, capture mode or
 *       required capabilities is dropped, using its {@code ProviderDescriptor}.</li>
 * </ul>
 * The rule ORDER (precedence) is set in {@code config.RoutingConfig}, from {@code application.yml}.
 * The service is pure (no I/O), so it is tested with plain JUnit.
 */
package com.designpatterns.level02.service.routing;
