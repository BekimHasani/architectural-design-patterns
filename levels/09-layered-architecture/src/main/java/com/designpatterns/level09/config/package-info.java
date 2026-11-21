/**
 * <h2>CONFIG: the composition root (FLOW S). Where objects are built and connected.</h2>
 *
 * <p><b>Dependency Injection / Inversion of Control:</b> classes don't create what they need; they ask for
 * it in their constructor and Spring hands it over. Most classes are found by <b>component scanning</b>
 * ({@code @Service}, {@code @Repository}, {@code @Component}, {@code @RestController}). This package builds
 * only what needs CONFIGURATION DATA or a decision:
 * <ul>
 *   <li>{@code PaymentProperties}: {@code @ConfigurationProperties("payment")} turns {@code application.yml}
 *       into a typed record. A typo becomes a startup error, not a runtime surprise.</li>
 *   <li>{@code ApplicationConfig}: the {@code Clock} (so tests can control time), {@code @EnableScheduling}.</li>
 *   <li>{@code PersistenceConfig}: the merchant settings store.</li>
 *   <li>{@code IntegrationConfig}: every enabled provider, each wrapped in {@code breaker(retry(call))}.</li>
 *   <li>{@code RoutingConfig}: routing rules in precedence order; fails fast on unknown provider names.</li>
 * </ul>
 * <p><b>Annotations:</b> {@code @Configuration} (a class holding bean recipes), {@code @Bean} (the return
 * value of this method is a bean; its parameters are injected).
 *
 * <p><b>Rule:</b> config never names a provider; providers register themselves via {@code ProviderFactory}.
 */
package com.designpatterns.level09.config;
