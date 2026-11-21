# Architecture Learning Path — Multi-Provider Payments

> **Single source of truth** for this repository. Every level, demo, and convention
> is defined here. If a level README disagrees with this file, this file wins —
> fix the level README.

---

## 1. Purpose

This repo teaches **source-code architecture** by building one realistic system —
a payment platform that charges customers through several payment providers —
twice, in two different architectural styles.

### The one question behind every level

> **What belongs to the business, what belongs to the outside world, and how do we
> stop changes in the outside world from contaminating the business?**

Pattern names (Hexagonal, Layered, Ports & Adapters, Strategy…) are useful vocabulary,
not the goal. A design that says "Clean Architecture" but leaks Stripe types into
the checkout logic has failed. A design that never uses the words but keeps the
boundary clean has succeeded.

---

## 2. Level map

| # | Level | Pressure | Core concepts | Folder |
|---|-------|----------|---------------|--------|
| 1 | Hexagonal architecture | 20 providers with different capabilities (refunds or not, hold or charge-only, sync or async, limited currencies) | Ports & adapters, interface segregation, capability model, leaky abstractions | [levels/01-hexagonal-architecture](levels/01-hexagonal-architecture/README.md) |
| 2 | Layered architecture | Same behaviour and HTTP API, rebuilt the way most Spring teams structure an app | Layers that only call downward, repositories owned by persistence, Spring Data JPA, transactions kept away from provider calls | [levels/02-layered-architecture](levels/02-layered-architecture/docs/CODE-GUIDE.md) |

Both levels implement the full platform: authorize / capture / cancel / refund,
routing, retry and circuit breaking, idempotency, reconciliation of unknown
outcomes, signed webhooks with an inbox, per-merchant configuration and an audit
trail. Comparing the two shows **who owns which interface** and what that costs.

---

## 3. How to work through a level

Don't skip steps 2 and 5; that's where most of the learning happens.

1. **Read the scenario** in the level README (Level 1) or the code guide (Level 2).
2. **Answer the questions in writing** before reading the code, in `levels/NN-*/NOTES.md`.
   Draw the folder tree and the dependency diagram.
3. **Read or extend** the code in `levels/NN-*/src` until the acceptance criteria pass.
4. **Compare** with the reference design (hidden in a `<details>` block in the
   README). Write down the differences and *why* they differ.
5. **Compare the two levels.** Open the same use case in both (e.g. Level 1's
   `PaymentOrchestrator` vs Level 2's `AuthorizationOrchestrator`). The logic is
   the same; list what moved and which dependency arrows flipped.

Level 2 is a rewrite of Level 1's behaviour, not a copy of its code: the diff
between them shows exactly what changing the architecture costs.

---

## 4. The shared scenario

Both levels use the same business, so only the architecture changes.

**Business:** an online shop platform. Merchants sell; customers pay; the system
charges the customer through an external payment provider chosen per payment.

### 4.1 Core domain vocabulary

| Term | Meaning | Owned by |
|------|---------|----------|
| **Money** | Amount in **integer minor units** + ISO-4217 currency (`Money.of(1999, "EUR")` = €19.99). Never floats. Beware zero-decimal currencies (JPY). | Domain |
| **Customer** | Who pays. Has an id; may belong to a merchant/enterprise account. | Domain |
| **Merchant** | The seller account; may have a configured provider and its own credentials. | Domain |
| **Payment** | Our internal record of "money we're trying to collect". Has a lifecycle (state machine). | Domain |
| **PaymentAttempt** | One try at one provider for one payment. A payment can have several attempts. | Domain |
| **Capability** | What a provider can do (authorize, capture, refund, …). Declared by each provider, checked before routing. | Domain (as an enum) |
| **Provider** | External system (Stripe, PayPal, Adyen, …). **Never** a domain concept beyond an opaque `ProviderId`. | Infrastructure |
| **Provider reference** | The provider's id for a transaction (e.g. a payment-intent id). Stored on the attempt, opaque to the domain. | Domain (as opaque string) |

### 4.2 The providers

We **do not call real provider APIs**. Each provider is a local simulator with a
deliberately different API shape, auth scheme, error format, and webhook format.
The simulators can inject failures on demand (timeouts, 5xx, declines, "charged
but response lost"). See [tools/provider-simulators](tools/provider-simulators/README.md).

The simulators are loosely *inspired by* Stripe, PayPal and Adyen, but simplified.
They are test tooling, **not** part of the architecture under study.

---

## 5. Architectural conventions

### 5.1 Level 1: hexagonal (ports & adapters)

```
            ┌──────────────────────────────────────────┐
            │  api  (HTTP controllers, idempotent      │
            │  requests, reconciliation entry points)  │
            └───────────────┬──────────────────────────┘
                            │ calls
            ┌───────────────▼──────────────────────────┐
            │  payments.application  (use cases,       │
            │  routing, capabilities, ports)           │
            └───────────────┬──────────────────────────┘
                            │ uses
            ┌───────────────▼──────────────────────────┐
            │  payments.domain  (Money, Payment, rules)│
            │  depends on NOTHING                      │
            └──────────────────────────────────────────┘
                            ▲
                            │ implements ports
            ┌───────────────┴──────────────────────────┐
            │  providers/*, platform (persistence,     │
            │  resilience), merchants, audit           │
            └──────────────────────────────────────────┘

  config/ (composition root): the ONLY place that knows every concrete class.
```

Rules (packages under `com.designpatterns.level01`, enforced by `architecture/ArchitectureTest`):

- `payments.domain` imports nothing outside itself and `java.*`.
- `payments.application` imports only the domain. **Ports** (`GatewayRegistry`,
  `ProviderEventInbox`, repositories) live in `application.ports`, because the
  consumer owns the interface.
- `providers/<name>` implement the capability interfaces. Provider DTOs are
  **package-private** so they cannot leak. Webhook controllers live in
  `providers/<name>/webhook/`.
- `api` translates HTTP ↔ application commands. No business rules.
- **The core is framework-free**: no Spring annotations in `payments`. Business
  classes are wired with `@Bean` methods in `config`, never `@Service`.

### 5.2 Level 2: layered

```
presentation → service → persistence | integration → model        (config wires everything)
```

Rules (packages under `com.designpatterns.level02`, enforced by `architecture/LayeredArchitectureTest`):

- `model` is pure Java: no `@Entity`, no Spring.
- `persistence` owns its repository interfaces (written in model types). JPA/Hibernate
  may only be used in `persistence.jpa`.
- `integration` holds the provider contract (`gateway/`), `resilience/`, `webhook/`
  translators and `provider/<name>/`. Each provider exposes only its gateway as public.
- `service` holds `@Service` use cases. No JPA, no web types, no concrete provider,
  and **no `@Transactional`**: a transaction must never stay open across a provider call.
- `presentation` holds REST controllers, one `POST /webhooks/{provider}` endpoint and
  the reconciliation scheduler.
- `persistence` and `integration` must not depend on each other. `config` must not
  name any provider; providers register through a `ProviderFactory` `@Component`.

### 5.3 Build and run

The root `pom.xml` only aggregates modules and pins versions; it holds no code.

```bash
./mvnw test                                               # both levels + simulators
./mvnw test -pl levels/01-hexagonal-architecture -am      # one level
./mvnw -q install -DskipTests                             # once, before running a single level
./mvnw -q exec:java -pl tools/provider-simulators         # start simulators (ports 4101–4107)
./mvnw -q spring-boot:run -pl levels/02-layered-architecture
```

Settings in `application.yml` can be overridden with environment variables
(Spring relaxed binding): `payment.provider` ← `PAYMENT_PROVIDER`,
`server.port` ← `SERVER_PORT`.

Tests start the simulators in-process on random ports, so `./mvnw test`
needs nothing else running.

### 5.4 Testing strategy

| Test type | What it proves |
|-----------|---------------|
| Unit tests with in-memory fakes (`InMemory*`, `ScriptedGateway`) | Business logic works with no provider at all, which is the proof the boundary is real |
| Provider contract kit (`GatewayContractTest`, one suite run against every adapter) | Every adapter honours the contract identically, with tests generated from its declared capabilities |
| Fault-injection tests via simulators | Correct behaviour on timeout / 5xx / decline / lost response |
| Concurrency / duplicate-request tests | Idempotency actually holds |
| Webhook replay / reorder tests | Async state converges correctly |
| Architecture tests (ArchUnit) | The dependency rule cannot silently erode |

`@SpringBootTest` is used only in `e2e/`, `@DataJpaTest` only in Level 2's
`persistence/jpa/`, and plain JUnit everywhere else.

### 5.5 Tech stack

| Decision | Choice | Why | Revisit if |
|----------|--------|-----|-----------|
| D1 Language | Java 21 (LTS) | Interfaces for ports; **sealed interfaces + records + pattern-matching `switch`** make result types explicit and force every outcome to be handled | — |
| D2 Build | Maven, via the committed wrapper `./mvnw` (no global install needed) | Standard, one module per level | — |
| D3 Framework | Spring Boot 3.3: `@RestController`, `RestClient` for provider calls, `@ConfigurationProperties`, `ProblemDetail` errors | Less boilerplate, more readable; it's what most Java teams use | — |
| D4 Tests | JUnit 5 + AssertJ | Business tests stay millisecond-fast with no Spring context | — |
| D5 Boundary rules | ArchUnit | Makes the dependency rule executable | — |
| D6 Persistence | **H2** (file DB when running, in-memory in tests) with a plain `schema.sql`. Level 1 uses Spring JDBC `JdbcClient`; Level 2 uses Spring Data JPA (`ddl-auto: none`) | Real unique-constraint races for the concurrency tests; plain SQL keeps the schema readable | You want production-like behaviour: PostgreSQL (Testcontainers) |
| D7 No real providers | Local simulators only | Deterministic, free, can inject faults | — |
| D8 Where Spring lives | Level 1: only at the edges, core wired via `@Bean`. Level 2: `@Service` in the service layer, as in a typical Spring app | The comparison is part of the lesson | — |
| D9 Simulators without Spring | Plain JDK `HttpServer` | Test tooling; starts in-process in milliseconds | — |

Each level keeps its own code; the only shared code is `tools/provider-simulators`,
which both levels use as a **test-scoped** dependency.

---

## 6. Cross-cutting concerns

| Concern | Level 1 | Level 2 |
|---------|---------|---------|
| Money correctness (minor units, currency checks) | `payments/domain` | `model` |
| Error translation (provider → internal categories) | `providers/<name>` | `integration/provider/<name>` |
| "Requires customer action" (PayPal approval, 3-D Secure) | sealed `GatewayResult` | sealed `GatewayResult` |
| Configuration | `config` only | `config` only |
| Timeouts, retry, circuit breaker on every outbound call | `platform/resilience` | `integration/resilience` |
| Idempotency (inbound) | `api/IdempotentRequests` | `presentation/rest/IdempotentRequestExecutor` + `service/idempotency` |
| Durable state + state machine | `payments/domain/PaymentStatus`, `platform/persistence` | `model/PaymentStatus`, `persistence/jpa` |
| Webhook signature verification + inbox | `providers/<name>/webhook`, `ProviderEventInbox` | `integration/provider/<name>/webhook`, `ProviderEventInboxRepository` |
| Audit history (append-only per payment) | `audit/AuditTrail` | `service/audit` + `AuditEventRepository` |
| Observability (correlation id, attempt id in logs) | log pattern in `application.yml` | log pattern in `application.yml` |
| Per-merchant credentials | `merchants` | `service/merchant` |

### Domain invariants

- A **timeout or lost response means "outcome unknown"**. Never fall back to another
  provider after one, because that can double-charge. Unknown outcomes are resolved
  by reconciliation (lookup by our attempt id). Only errors that prove no charge
  happened are safe to fall back on.
- "Requires customer action" cannot be hidden in an adapter. It is an explicit result outcome.
- Money-moving API commands require an `Idempotency-Key` header, scoped to merchant + path.
- Provider webhooks are verified (signature) before anything is stored, and deduplicated
  through the inbox.

---

## 7. Self-assessment: the signals that matter

| Level | You've got it if… |
|-------|-------------------|
| 1 | You can explain where a single `PaymentProvider` interface leaks, and you choose a capability model deliberately, not by accident. A new provider is added by writing an adapter + config only. |
| 2 | You can say which interfaces changed owner compared with Level 1, why services must not use `@Transactional` here, and when you would pick layered over hexagonal (and back). |

---

## 8. Glossary of patterns (learn the idea, then the name)

| Name | One-line idea | Where to look |
|------|---------------|---------------|
| Dependency Inversion | High-level policy depends on abstractions it owns; details implement them | L1 `payments/application/ports` |
| Ports & Adapters / Hexagonal | The app defines ports; the outside world plugs in adapters | L1 (whole module) |
| Layered architecture | Each layer calls only the layers below it | L2 (whole module) |
| Adapter | Translate one interface/model into another | L1 `providers/<name>`, L2 `integration/provider/<name>` |
| Anti-Corruption Layer | A translation boundary that keeps a foreign model out of yours | Provider DTOs + mappers, webhook translators |
| Strategy | Swap an algorithm behind an interface | `RoutingRule`; L2 `ProviderEventTranslator` |
| Registry vs Factory | Look up an existing instance by key vs construct a new one | L1 `GatewayRegistry` + `ProviderFactory`; L2 `ProviderFactory`, `WebhookTranslators` |
| Chain of Responsibility / Rule list | Ordered rules, first match wins | L1 `ProviderRouter`, L2 `ProviderRoutingService` |
| Facade | One entry point hiding several collaborators | L2 `ProviderGateway` |
| Decorator vs wrapping the call | Adding retry/timeout around a port; with many capability interfaces the call is wrapped instead | `RetryPolicy` (both levels) |
| Circuit Breaker | Stop calling a failing dependency for a while | `CircuitBreakerPolicy` (both levels) |
| Idempotency Key | Same request id → same effect, at most once | §6 Idempotency row |
| State Machine | Explicit allowed transitions between payment states | `PaymentStatus` (both levels) |
| Reconciliation | Periodically ask the provider for the truth and repair our state | L1 `ReconcileUnknownPayments`, L2 `ReconciliationService` |
| Inbox | Durably record incoming messages to process them exactly-once-in-effect | L1 `ProviderEventInbox`, L2 `ProviderEventInboxRepository` |
| Repository | Collection-like access to aggregates, hiding SQL/JPA | L1 `application/ports`, L2 `persistence` |
| Interface Segregation | Many small capability interfaces beat one fat one | `Capturer`, `Refunder`, … ([ADR-0005](levels/01-hexagonal-architecture/docs/adr/0005-capability-model.md)) |
