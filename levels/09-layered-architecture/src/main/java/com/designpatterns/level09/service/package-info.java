/**
 * <h2>SERVICE layer: the use cases. Where the business steps are.</h2>
 *
 * <p><b>Job:</b> one public method per thing the business does ("authorize a payment", "refund it",
 * "handle a webhook", "reconcile"). A service loads model objects from persistence, asks them to change
 * (they check the rules), calls providers through integration, and saves the result.
 *
 * <p><b>Pattern: Service Layer</b> (Fowler). The controllers stay thin and the model stays pure; the
 * coordination between them lives here.
 *
 * <p><b>Annotation {@code @Service}:</b> marks the class as a Spring bean (like {@code @Component}, with a
 * clearer name). Spring creates ONE instance and passes its constructor arguments
 * (<b>constructor injection</b>). Fields are {@code final}, so a service is ready to use once built and
 * easy to create by hand in a unit test.
 *
 * <p><b>Rules (ArchUnit):</b>
 * <ul>
 *   <li><b>No {@code @Transactional} here.</b> A service method calls providers over the network, which can
 *       take seconds. A transaction held open that long locks rows and, worse, a rollback can't undo money
 *       the provider already moved. So each {@code repository.save(...)} is its own short transaction.</li>
 *   <li>No JPA, Spring Data or web types; no concrete provider class (only the gateway contract).</li>
 * </ul>
 *
 * <p><b>Ideas used across all services:</b>
 * <ul>
 *   <li><b>Write-ahead</b>: save "we are about to call provider X with attempt id Y" BEFORE calling. If we
 *       crash during the call, reconciliation still knows what to ask about.</li>
 *   <li><b>Optimistic concurrency, reload and retry</b>: if {@code save} throws {@code ConcurrentUpdateException}
 *       (someone saved first, e.g. a webhook), load the latest copy, apply our change again, save again.</li>
 *   <li><b>Unknown outcome is not failure</b>: after a timeout the payment becomes UNKNOWN and FLOW D settles it.</li>
 * </ul>
 */
package com.designpatterns.level09.service;
