# CLAUDE.md

## What this repo is

A learning repo that teaches source-code architecture through one problem: a payment platform that charges customers through several payment providers. Each level is a self-contained Maven module: Level 1 builds it as a hexagonal architecture, Level 2 rebuilds the same behaviour as a layered architecture, and Level 3 is a smaller layered version with planted issues, used as a review challenge. [Architecture.md](Architecture.md) is the **single source of truth** for the level map, conventions, domain vocabulary and tech stack. If a level README disagrees with it, Architecture.md wins and the README should be fixed.

## Current state of the working tree

On disk: `levels/01-hexagonal-architecture`, `levels/02-layered-architecture`, `levels/03-architectural-challenge` and `tools/provider-simulators`. The root `pom.xml` lists exactly these four modules, so reactor builds from the root work.

**Level 2 is the active level.** It is a layered rewrite of Level 1 with the same behaviour and HTTP API, using Spring Data JPA for persistence. Its target design is in `levels/02-layered-architecture/docs/LAYERED-ARCHITECTURE-PROPOSAL.md`, and the reading guide in `docs/CODE-GUIDE.md`. The code carries `FLOW A–D, S` step comments and a `package-info.java` guide per layer; keep them accurate when changing a flow. Level 1 stays as the hexagonal reference to compare against; don't change it while working on Level 2.

```bash
./mvnw test                                                              # everything (sims + L1 + L2)
./mvnw -f levels/02-layered-architecture/pom.xml test                    # level 2 only
./mvnw -f levels/02-layered-architecture/pom.xml test -Dtest=ProviderRoutingServiceTest
./mvnw -f levels/02-layered-architecture/pom.xml test -Dtest='GatewayContractTest*'
./mvnw -f levels/02-layered-architecture/pom.xml test -Dtest=LayeredArchitectureTest
./mvnw -f tools/provider-simulators/pom.xml install                      # after changing a simulator
./mvnw -q install -DskipTests                                            # before running a level
./mvnw -q exec:java -pl tools/provider-simulators                        # start sims (ports 4101–4107)
./mvnw -q spring-boot:run -pl levels/02-layered-architecture             # app on :8080
```

Tests start the simulators in-process on random ports, so nothing else needs to be running. Env vars override `application.yml` through Spring relaxed binding (`PAYMENT_PROVIDER`, `SERVER_PORT`). Level 2 uses a file H2 DB at `./target/level02-db` (Level 1: `./target/level01-db`). `schema.sql` owns the tables; Hibernate runs with `ddl-auto: none` and `open-in-view: false`.

## Level 3 (architectural challenge): the bugs are intentional

`levels/03-architectural-challenge` (package `com.designpatterns.level03`) is a compact Level 2-style app with **5 planted issues** (2 architectural, 3 technical) plus deliberate smells. **Do not fix them, or "clean up" the smells, unless the user asks you to solve the challenge.** The answers and reference fixes are in the `<details>` blocks of its README; don't reveal them unprompted.

Tests tagged `challenge` (JUnit `@Tag`, ArchUnit `@ArchTag`) detect the issues and fail until they are fixed. The level's `pom.xml` excludes them by default; `-Pchallenge` runs only them. Expected state: default build 63 green; `-Pchallenge` 8 failing. A change to Level 3 must keep both numbers, and the reference fixes must still turn the 8 green without test changes.

```bash
./mvnw -f levels/03-architectural-challenge/pom.xml test               # 63 tests, green
./mvnw -f levels/03-architectural-challenge/pom.xml test -Pchallenge   # 8 tests, red by design
```

## Level 2 architecture (enforced by `architecture/LayeredArchitectureTest`, a violating import fails the build)

Layers under `com.designpatterns.level02`, dependencies only point down:

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

## Level 1 (hexagonal reference)

Packages `payments` (framework-free core: `domain` + `application` with ports), `providers/*`, `platform`, `merchants`, `audit`, `api` and `config`, enforced by `architecture/ArchitectureTest`. Ports live with the consumer, and business classes are wired with `@Bean` methods, never `@Service`. Webhook controllers live in `providers/<name>/webhook/`. Build it with `./mvnw -f levels/01-hexagonal-architecture/pom.xml test`.

## Domain invariants worth remembering

- Money is integer minor units + ISO-4217 currency (`Money.of(1999, "EUR")`), never floats. Watch out for zero-decimal currencies (JPY). The PayPal-like sim uses decimal strings, so adapters convert.
- A **timeout or lost response means "outcome unknown"**. Never fall back to another provider after one, because that can double-charge. Unknown outcomes are resolved by reconciliation (lookup by our attempt id). Only errors that prove no charge happened are safe to fall back on.
- "Requires customer action" (PayPal approval, 3-D Secure) cannot be hidden in an adapter. It is an explicit result outcome.
- Money-moving API commands require an `Idempotency-Key` header, scoped to merchant + path (L2: `presentation/rest/IdempotentRequestExecutor` + `service/idempotency/IdempotencyService`).
- Provider webhooks are verified (signature) before anything is stored, and deduplicated through the inbox (`ProviderEventInboxRepository`).

## Provider simulators (`tools/provider-simulators`)

Plain JDK `HttpServer`, no Spring. This is test tooling, so don't apply the levels' patterns here. Levels use it as a test-scoped dependency. It includes Stripe-/PayPal-/Adyen-like sims (4101–4103), Provider X (4104) and `SyntheticSimulator` in HOLD/CHARGE/ASYNC modes (4105–4107). Faults are injected with `sim.injectFault(Fault.X, n)`, the `X-Sim-Fault` header or `POST /sim/faults`. `ledger()` / `GET /sim/ledger` shows what was really charged. See its README for API shapes, webhook signatures and the list of faults implemented so far.

## Level workflow conventions

- Level READMEs keep the reference design inside a `<details>` block ("open only after writing NOTES.md"). Learner answers go in `levels/NN-*/NOTES.md`.
- Test packages mirror main packages. Use `@SpringBootTest` only for `e2e/`, `@DataJpaTest` only for `persistence/jpa/`, and plain JUnit with in-memory fakes (`support/InMemory*`, `ScriptedGateway`) everywhere else.
- This is a learning repo: when changing a level, explain the pattern and the reason behind the change, not just the code.
