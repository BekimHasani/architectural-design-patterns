# CLAUDE.md

## What this repo is

A learning path that teaches source-code architecture through one evolving problem: a checkout that charges customers through several payment providers. Each level is a self-contained Maven module that adds one new requirement. [Architecture.md](Architecture.md) is the **single source of truth** for the level map, conventions, domain vocabulary and tech stack. If a level README disagrees with it, Architecture.md wins and the README should be fixed.

## Current state of the working tree

On disk: `levels/08-hexagonal-architecture`, `levels/09-layered-architecture`, `levels/_template` and `tools/provider-simulators`. The root `pom.xml` lists exactly the simulators, level 8 and level 9, so reactor builds from the root work.

**Level 9 is the active level.** It is a layered rewrite of Level 8 with the same behaviour and HTTP API, using Spring Data JPA for persistence. Its target design is in `levels/09-layered-architecture/docs/LAYERED-ARCHITECTURE-PROPOSAL.md`, and the reading guide in `docs/CODE-GUIDE.md`. The code carries `FLOW A–D, S` step comments and a `package-info.java` guide per layer; keep them accurate when changing a flow. Level 8 stays as the hexagonal reference to compare against; don't change it while working on Level 9.

```bash
./mvnw test                                                              # everything (sims + L8 + L9)
./mvnw -f levels/09-layered-architecture/pom.xml test                    # level 9 only
./mvnw -f levels/09-layered-architecture/pom.xml test -Dtest=ProviderRoutingServiceTest
./mvnw -f levels/09-layered-architecture/pom.xml test -Dtest='GatewayContractTest*'
./mvnw -f levels/09-layered-architecture/pom.xml test -Dtest=LayeredArchitectureTest
./mvnw -f tools/provider-simulators/pom.xml install                      # after changing a simulator
./mvnw -q install -DskipTests                                            # before running a level
./mvnw -q exec:java -pl tools/provider-simulators                        # start sims (ports 4101–4107)
./mvnw -q spring-boot:run -pl levels/09-layered-architecture             # app on :8080
```

Tests start the simulators in-process on random ports, so nothing else needs to be running. Env vars override `application.yml` through Spring relaxed binding (`PAYMENT_PROVIDER`, `SERVER_PORT`). Level 9 uses a file H2 DB at `./target/level09-db` (Level 8: `./target/level08-db`). `schema.sql` owns the tables; Hibernate runs with `ddl-auto: none` and `open-in-view: false`.

## Level 9 architecture (enforced by `architecture/LayeredArchitectureTest`, a violating import fails the build)

Layers under `com.designpatterns.level09`, dependencies only point down:

```
presentation → service → persistence | integration → model        (config wires everything)
```

- `model/`: the domain (`Payment` aggregate, `Money`, ids, `Capability`, domain exceptions including `ConcurrentUpdateException`). **Pure Java**: depends only on itself and `java.*`. No `@Entity`, no Spring.
- `persistence/`: repository interfaces owned by this layer, written in model types (`PaymentRepository`, `PaymentAttemptRepository`, `ProviderEventInboxRepository`, `IdempotencyKeyRepository`, `AuditEventRepository`, `MerchantConfigRepository`). `persistence/jpa/` holds the package-private `@Entity` classes, Spring Data interfaces (one top-level file each; Spring Data doesn't scan nested ones) and `Jpa*Repository` adapters that map entity ↔ model. **JPA/Hibernate may only be used in `persistence.jpa`.**
- `integration/`: the provider contract (`gateway/`: `ProviderAdapter`, capability interfaces, `CapabilityBinding`, sealed `GatewayResult`, the `ProviderGateway` facade, `ProviderFactory`), `resilience/` (retry + circuit breaker), `support/`, `webhook/` (`ProviderEventTranslator` strategy, `WebhookTranslators` registry) and `provider/<name>/`.
- `service/`: `@Service` use cases (payment, routing, event, reconciliation, merchant, idempotency, audit). Services may not use Spring Data/JPA/web types, may not reference a concrete provider, and may not use **`@Transactional`**: a transaction must never stay open across a provider call, so transactions are opened only inside persistence with `TransactionTemplate`.
- `presentation/`: `rest/` controllers, DTOs and `ApiExceptionHandler` (ProblemDetail); `webhook/ProviderWebhookController` (one `POST /webhooks/{provider}` for every provider); `scheduling/ReconciliationScheduler`. Controllers live only here.
- `config/`: the composition root and the only place that reads configuration. It must **not name any provider**: providers register themselves through a `ProviderFactory` `@Component`.
- `persistence` and `integration` must not depend on each other.
- Each `integration/provider/<name>/` exposes only its gateway as public. DTOs, mappers, clients and the webhook translator are package-private. Providers may not depend on each other (except `integration/support`).

Design choices to keep:
- A payment's `version` is a plain column, not JPA `@Version`. `JpaPaymentRepository` locks the row (`SELECT … FOR UPDATE`), checks the version and writes it +1 in one short transaction. A lost race or a lock timeout becomes `ConcurrentUpdateException`, and services reload, apply and save again.
- Insert-only rows (new payments, inbox events, idempotency keys) implement `Persistable`, so `save()` inserts and fails on a duplicate instead of silently overwriting.
- How a `GatewayResult` changes a `Payment` is decided only in `service/payment/GatewayResultApplier`.

## Level 8 (hexagonal reference)

Packages `payments` (framework-free core: `domain` + `application` with ports), `providers/*`, `platform`, `merchants`, `audit`, `api` and `config`, enforced by `architecture/ArchitectureTest`. Ports live with the consumer, and business classes are wired with `@Bean` methods, never `@Service`. Webhook controllers live in `providers/<name>/webhook/`. Build it with `./mvnw -f levels/08-hexagonal-architecture/pom.xml test`.

## Domain invariants worth remembering

- Money is integer minor units + ISO-4217 currency (`Money.of(1999, "EUR")`), never floats. Watch out for zero-decimal currencies (JPY). The PayPal-like sim uses decimal strings, so adapters convert.
- A **timeout or lost response means "outcome unknown"**. Never fall back to another provider after one, because that can double-charge. Unknown outcomes are resolved by reconciliation (lookup by our attempt id). Only errors that prove no charge happened are safe to fall back on.
- "Requires customer action" (PayPal approval, 3-D Secure) cannot be hidden in an adapter. It is an explicit result outcome.
- Money-moving API commands require an `Idempotency-Key` header, scoped to merchant + path (L9: `presentation/rest/IdempotentRequestExecutor` + `service/idempotency/IdempotencyService`).
- Provider webhooks are verified (signature) before anything is stored, and deduplicated through the inbox (`ProviderEventInboxRepository`).

## Provider simulators (`tools/provider-simulators`)

Plain JDK `HttpServer`, no Spring. This is test tooling, so don't apply the levels' patterns here. Levels use it as a test-scoped dependency. It includes Stripe-/PayPal-/Adyen-like sims (4101–4103), Provider X (4104) and `SyntheticSimulator` in HOLD/CHARGE/ASYNC modes (4105–4107). Faults are injected with `sim.injectFault(Fault.X, n)`, the `X-Sim-Fault` header or `POST /sim/faults`. `ledger()` / `GET /sim/ledger` shows what was really charged. See its README for API shapes, webhook signatures and the list of faults implemented so far.

## Level workflow conventions

- Level READMEs keep the reference design inside a `<details>` block ("open only after writing NOTES.md"). Learner answers go in `levels/NN-*/NOTES.md`.
- Test packages mirror main packages. Use `@SpringBootTest` only for `e2e/`, `@DataJpaTest` only for `persistence/jpa/`, and plain JUnit with in-memory fakes (`support/InMemory*`, `ScriptedGateway`) everywhere else.
- This is a learning repo: when changing a level, explain the pattern and the reason behind the change, not just the code.
