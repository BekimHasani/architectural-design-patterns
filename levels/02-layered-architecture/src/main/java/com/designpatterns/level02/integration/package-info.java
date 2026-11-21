/**
 * <h2>INTEGRATION layer: talking to payment providers.</h2>
 *
 * <p><b>Job:</b> hide every provider's API (URLs, JSON, error codes, signatures) behind one contract
 * written in our words. Services call {@link com.designpatterns.level02.integration.gateway.ProviderGateway}
 * and get back a {@link com.designpatterns.level02.integration.gateway.GatewayResult}; they never see a
 * Stripe or PayPal class.
 *
 * <p><b>Sub-packages:</b>
 * <ul>
 *   <li>{@code gateway}: the contract every provider fulfils, and the facade services use.</li>
 *   <li>{@code gateway.capability}: one small interface per thing a provider can do.</li>
 *   <li>{@code resilience}: retry and circuit breaker, wrapped around every call.</li>
 *   <li>{@code webhook}: the contract for turning a provider's webhook into our {@code ProviderEvent}.</li>
 *   <li>{@code provider.<name>}: one folder per provider (adapter, mapper, JSON shapes, webhook translator).</li>
 *   <li>{@code support}: HTTP helpers shared by providers.</li>
 * </ul>
 *
 * <p><b>Rules (ArchUnit):</b> providers don't know each other; only the gateway class of a provider is
 * public; integration and persistence don't depend on each other.
 */
package com.designpatterns.level02.integration;
