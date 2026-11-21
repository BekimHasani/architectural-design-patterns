/**
 * <h2>The provider contract and the facade in front of all providers.</h2>
 *
 * <p><b>Patterns:</b>
 * <ul>
 *   <li><b>Adapter</b>: each provider class (e.g. {@code StripeGateway}) implements
 *       {@link com.designpatterns.level09.integration.gateway.ProviderAdapter} and converts our requests to
 *       the provider's API and its answers back to {@code GatewayResult}.</li>
 *   <li><b>Interface Segregation</b>: instead of one big interface, one per capability ({@code Authorizer},
 *       {@code Capturer}, {@code Refunder}, ...). A provider implements only what it really can do, so no
 *       method has to throw "not supported".</li>
 *   <li><b>Sealed result type</b> ({@code GatewayResult}): every possible answer is a record; {@code switch}
 *       statements over it must handle all cases, or the code doesn't compile. Errors are values, not
 *       exceptions, because "declined" or "timed out" are normal answers in payments.</li>
 *   <li><b>Facade + Registry</b> ({@code ProviderGateway} / {@code DefaultProviderGateway}): one object that
 *       knows all providers by id and runs each call through the resilience policy.</li>
 *   <li><b>Plug-in Factory</b> ({@code ProviderFactory}): each provider has a {@code @Component} factory. Spring
 *       finds them all; config never names a provider (Open/Closed: add a folder, change nothing else).</li>
 *   <li><b>Fail fast</b>: {@code DefaultProviderGateway} refuses to start if a provider's descriptor
 *       ("I can REFUND") disagrees with its code (does it implement {@code Refunder}?).</li>
 * </ul>
 *
 * <p><b>The most important rule in this package</b> is in {@code FailureCategory}: a timeout means
 * "outcome unknown". Never retry it and never try another provider, or the customer may pay twice.
 */
package com.designpatterns.level09.integration.gateway;
