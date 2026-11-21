/**
 * <h2>PERSISTENCE layer: storing and loading. The interfaces services use.</h2>
 *
 * <p><b>Job:</b> remember things between requests: payments, attempts, received webhooks, idempotency keys,
 * the audit trail, merchant settings.
 *
 * <p><b>Pattern: Repository.</b> Each interface here looks like a collection of model objects
 * ({@code save}, {@code find}), so a service never sees SQL, tables or JPA. The interfaces are written in
 * MODEL types; the JPA classes stay hidden in {@code persistence.jpa}.
 *
 * <p><b>Layered vs hexagonal (the key difference with Level 8):</b> in Level 8 the CORE owned these
 * interfaces ("ports") and the database code depended on the core. Here the interfaces are OWNED BY THE
 * PERSISTENCE LAYER and the service layer depends on them downwards. We still keep interfaces (instead of
 * using the JPA classes directly) for one practical reason: service tests can use fast in-memory fakes
 * ({@code support/InMemory*Repository} in the tests).
 *
 * <p><b>Rule:</b> persistence must not depend on integration (and the other way round). ArchUnit checks it.
 *
 * <p><b>FLOW S:</b> {@code PersistenceConfig} builds the merchant store; the JPA adapters are found by
 * scanning because they are {@code @Repository} classes.
 */
package com.designpatterns.level09.persistence;
