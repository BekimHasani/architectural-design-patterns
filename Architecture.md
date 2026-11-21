# Architecture Learning Path — Multi-Provider Payments

> **Single source of truth** for this repository. Every level, demo, and convention
> is defined here. If a level README disagrees with this file, this file wins —
> fix the level README.
>
> The original interview-style draft is archived in
> [docs/original-interview-script.md](docs/original-interview-script.md).

---

## 1. Purpose

This repo is a sequence of small, runnable demo projects that teach **source-code
architecture** by solving one realistic problem — a checkout that charges
customers through several payment providers — and then making the problem harder,
one requirement at a time.

Each level:

1. introduces **one new pressure** (a new requirement),
2. forces **one new architectural decision**,
3. is implemented as a **self-contained project** you can run and test,
4. ends with the requirement that **breaks** it — which becomes the next level.

### The one question behind every level

> **What belongs to the business, what belongs to the outside world, and how do we
> stop changes in the outside world from contaminating the business?**

Pattern names (Hexagonal, Clean, Ports & Adapters, Strategy…) are useful vocabulary,
not the goal. A design that says "Clean Architecture" but leaks Stripe types into
the checkout logic has failed. A design that never uses the words but keeps the
boundary clean has succeeded.

---

## 2. Level map (easy → expert)

| #  | Level | New pressure | Core concept learned | Folder |
|----|-------|--------------|----------------------|--------|
| 0  | Naive checkout | "Just make it work with Stripe" | Why architecture matters (feel the pain) | [levels/00-naive-checkout](levels/00-naive-checkout/README.md) |
| 1  | Provider abstraction | Switch provider by config | Dependency inversion, ports | [levels/01-provider-abstraction](levels/01-provider-abstraction/README.md) |
| 2  | Adapters & mapping | Providers have different APIs, workflows, errors | Adapters, anti-corruption layer, result modeling | [levels/02-adapters-and-mapping](levels/02-adapters-and-mapping/README.md) |
| 3  | Provider routing | Choose provider by currency / merchant / config | Strategy, rules, registry vs factory | [levels/03-provider-routing](levels/03-provider-routing/README.md) |
| 4  | Failures & fallback | Timeouts, 5xx, declines | Error classification, retry, circuit breaker, safe fallback | [levels/04-failures-and-fallback](levels/04-failures-and-fallback/README.md) |
| 5  | Idempotency & unknown outcomes | "Did Stripe charge or not?" | Idempotency keys, state machine, reconciliation | [levels/05-idempotency-and-unknown-outcomes](levels/05-idempotency-and-unknown-outcomes/README.md) |
| 6  | Webhooks & async state | Final status arrives later, in 3 formats | Inbound adapters, inbox, out-of-order events | [levels/06-webhooks-and-async-state](levels/06-webhooks-and-async-state/README.md) |
| 7  | Full payment platform | Authorize / capture / refund / audit / many merchants | Domain modeling, module boundaries, ADRs | [levels/07-full-payment-platform](levels/07-full-payment-platform/README.md) |
| 8  | Scaling to 20 providers | Providers with different capabilities | Leaky abstractions, interface segregation, capability model | [levels/08-scaling-to-many-providers](levels/08-hexagonal-architecture/README.md) |

Levels 0–3 are about **structure**. Levels 4–6 are about **distributed-systems
reality**. Levels 7–8 are about **whether your abstractions survive growth**.

---

## 3. How to work through a level

Every level follows the same loop. Don't skip steps 2 and 6; that's where most of
the learning happens.

1. **Read the scenario** in the level README.
2. **Answer the questions in writing** before coding, in `levels/NN-*/NOTES.md`.
   Draw the folder tree and the dependency diagram.
3. **Implement** the demo in `levels/NN-*/src` until the acceptance criteria pass.
4. **Compare** with the reference design (hidden in a `<details>` block in the
   README). Write down the differences and *why* they differ.
5. **Check the anti-patterns** list against your code.
6. **Apply the next level's requirement to your code, on paper.** List what you
   would have to change. That list is the motivation for the next level.

Each level **starts as a copy of the previous level's finished code**. That way
the diff between two levels shows exactly what the new requirement cost.

---

## 4. The shared scenario

All levels use the same business, so only the architecture changes.

**Business:** an online shop. A customer checks out a cart; the system calculates
the order total and charges the customer through an external payment provider.

### 4.1 Core domain vocabulary

| Term | Meaning | Owned by |
|------|---------|----------|
| **Money** | Amount in **integer minor units** + ISO-4217 currency (`Money.of(1999, "EUR")` = €19.99). Never floats. Beware zero-decimal currencies (JPY). | Domain |
| **Order** | Cart lines + calculated total. The checkout charges an order. | Domain |
| **Customer** | Who pays. Has an id; may belong to a merchant/enterprise account. | Domain |
| **Merchant** | (Level 3+) The seller account; may have a configured provider and its own credentials. | Domain |
| **Payment** | Our internal record of "money we're trying to collect for an order". Has a lifecycle (state machine from Level 5). | Domain |
| **PaymentAttempt** | (Level 4+) One try at one provider for one payment. A payment can have several attempts. | Domain |
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

## 5. Architectural conventions (apply from Level 1 onward)

### 5.1 Layers and the dependency rule

```
            ┌──────────────────────────────────────────┐
            │  api / entrypoints  (HTTP controllers,   │
            │  webhook endpoints, CLI, jobs)           │
            └───────────────┬──────────────────────────┘
                            │ calls
            ┌───────────────▼──────────────────────────┐
            │  application  (use cases, ports)         │
            │  CheckoutService, PaymentProvider port   │
            └───────────────┬──────────────────────────┘
                            │ uses
            ┌───────────────▼──────────────────────────┐
            │  domain  (Money, Order, Payment, rules)  │
            │  depends on NOTHING                      │
            └──────────────────────────────────────────┘
                            ▲
                            │ implements ports, imports application + domain
            ┌───────────────┴──────────────────────────┐
            │  infrastructure  (Stripe/PayPal/Adyen    │
            │  adapters, DB repositories, HTTP clients)│
            └──────────────────────────────────────────┘

  From Level 7 the same rules apply between modules: payments (core), merchants,
  audit, providers/*, platform, api. See levels/07-.../docs/adr/0001.

  config/ (composition root, Spring @Configuration): the ONLY place that knows
  every concrete class and wires them together (reads config, picks adapters).
```

Rules (each layer is a Java package under `com.designpatterns.levelNN`):

- `domain` imports nothing outside `domain` and `java.*`.
- `application` imports only `domain`. **Ports** (interfaces the application
  needs, such as `PaymentProvider` and `PaymentRepository`) live in
  `application.ports`, because the consumer owns the interface.
- `infrastructure` implements ports. Provider DTOs are **package-private** records
  inside their adapter package, so they cannot leak.
- `api` translates HTTP ↔ application commands. No business rules.
- `config` is the composition root. Config properties are read here and nowhere else.
- **`domain` and `application` are framework-free**: no Spring annotations, no
  HTTP or JSON libraries. Spring is used at the edges (`api`, `infrastructure`,
  `config`), and the business code is wired with `@Bean` methods instead of
  `@Service`. That way the framework is a replaceable detail too.

These rules are enforced by an **ArchUnit** test (`architecture/ArchitectureTest`)
from Level 1 onward: a violating import fails the build.

### 5.2 Standard folder shape per level

Each level is a Maven module. Test packages mirror the main packages, so the
kind of test is visible from the package it lives in.

```
levels/NN-name/
├── README.md          # scenario, questions, reference design, acceptance criteria
├── NOTES.md           # YOUR answers and design before coding (you write this)
├── pom.xml
└── src/
    ├── main/java/com/designpatterns/levelNN/
    │   ├── domain/            # Money, Order, … (pure Java)
    │   ├── application/
    │   │   ├── ports/         # interfaces the use cases need
    │   │   └── checkout/      # use cases
    │   ├── infrastructure/
    │   │   └── payments/<provider>/   # one package per adapter
    │   ├── api/               # @RestController, request/response records, error mapping
    │   ├── config/            # composition root: @Configuration + @ConfigurationProperties
    │   └── CheckoutApplication.java   # @SpringBootApplication
    ├── main/resources/application.yml
    └── test/java/com/designpatterns/levelNN/
        ├── domain/            # unit tests, no I/O
        ├── application/       # use-case tests with fake/lambda ports
        ├── infrastructure/    # adapter tests against the simulators
        ├── e2e/               # HTTP in → simulator → result
        └── architecture/      # ArchUnit dependency rules
```

Level 0 deliberately breaks this shape.

### 5.2.1 Build and run

The root `pom.xml` only aggregates modules and pins versions; it holds no code.

```bash
./mvnw test                                             # every level + simulators
./mvnw test -pl levels/01-provider-abstraction -am      # one level
./mvnw -q install -DskipTests                           # once, before running a single level
./mvnw -q exec:java -pl tools/provider-simulators       # start simulators (port 4101, …)
PAYMENT_PROVIDER=stripe ./mvnw -q spring-boot:run -pl levels/01-provider-abstraction
```

Settings in `application.yml` can be overridden with environment variables
(Spring relaxed binding): `payment.provider` ← `PAYMENT_PROVIDER`,
`server.port` ← `SERVER_PORT`.

Tests start the simulators in-process on random ports, so `./mvnw test` needs
nothing else running.

### 5.3 Testing strategy (grows per level)

| Test type | What it proves | Introduced |
|-----------|---------------|------------|
| Unit tests with a `FakePaymentProvider` | Business logic works with no provider at all, which is the proof the boundary is real | L1 |
| Adapter contract tests (same suite run against every adapter) | Every adapter honours the port's contract identically | L2 |
| Fault-injection tests via simulators | Correct behaviour on timeout / 5xx / decline / lost response | L4 |
| Concurrency / duplicate-request tests | Idempotency actually holds | L5 |
| Webhook replay / reorder tests | Async state converges correctly | L6 |
| Architecture tests (ArchUnit) | Dependency rule cannot silently erode | L1 |

### 5.4 Tech stack

| Decision | Choice | Why | Revisit if |
|----------|--------|-----|-----------|
| D1 Language | Java 21 (LTS) | Interfaces for ports; **sealed interfaces + records + pattern-matching `switch`** make result types explicit and force every outcome to be handled | — |
| D2 Build | Maven, via the committed wrapper `./mvnw` (no global install needed) | Standard, one module per level | — |
| D3 Framework | Spring Boot 3.3 (`spring-boot-starter-web`): `@RestController`, `RestClient` for provider calls, `@ConfigurationProperties`, `ProblemDetail` errors | Less boilerplate, more readable; it's what most Java teams use | — |
| D4 Tests | JUnit 5 + AssertJ; `@SpringBootTest` only for e2e, plain JUnit everywhere else | Business tests stay millisecond-fast with no Spring context | — |
| D5 Boundary rules | ArchUnit | Makes the dependency rule executable | — |
| D6 Persistence | In-memory repos until L4; from L5, **H2** (file DB when running, in-memory in tests) via Spring JDBC `JdbcClient` and a plain `schema.sql`. *Changed from SQLite*: SQLite allows one writer at a time, while the L5 concurrency tests need real unique-constraint races, and Spring Boot configures H2 itself | Introduce a DB only when a requirement needs it; plain SQL so the persistence adapter stays readable | You want production-like behaviour: PostgreSQL (Testcontainers) |
| D7 No real providers | Local simulators only | Deterministic, free, can inject faults | — |
| D8 Spring stays at the edges | Business classes have no annotations; wired via `@Bean` in `config/` | The framework is a detail, like Stripe; business code can be tested and reused without it | — |
| D9 Simulators without Spring | Plain JDK `HttpServer` | Test tooling; starts in-process in milliseconds | — |

Each level keeps its own code; the only shared code is `tools/provider-simulators`,
which levels use as a **test-scoped** dependency.

---

## 6. Cross-cutting concerns, and the level where each becomes mandatory

| Concern | First required | Notes |
|---------|---------------|-------|
| Money correctness (minor units, currency checks) | L1 | Domain value object |
| Error translation (provider → internal categories) | L2 | Adapter responsibility |
| "Requires customer action" flows (PayPal approval, 3-D Secure) | L2 | Cannot be hidden inside an adapter; must be in the result model |
| Configuration | L1 (provider), L3 (rules) | Read only in composition root |
| Timeouts on every outbound call | L4 | No call without a deadline |
| Idempotency (inbound and outbound) | L5 | |
| Durable state + state machine | L5 | |
| Webhook signature verification | L6 | Security boundary |
| Audit history | L7 | Append-only event log per payment |
| Observability (correlation id, attempt id in logs) | L4, expanded in L7 | |
| Secrets / per-merchant credentials | L7 | |

---

## 7. Self-assessment: the signals that matter at each level

| Level | You've got it if… |
|-------|-------------------|
| 0 | You can list concretely why adding PayPal to the naive code hurts. |
| 1 | `CheckoutService` compiles and is fully tested **with zero provider code present**. |
| 2 | Deleting an adapter folder breaks nothing except the composition root. The PayPal two-step capture is invisible to the application, but "customer must approve" is not, and you can explain why. |
| 3 | Adding a routing rule doesn't modify `CheckoutService`. You can draw the dependency diagram and every arrow points inward. |
| 4 | You can say which errors are safe to fall back on, and that **a timeout is not one of them**. |
| 5 | Double-clicking "Pay", or retrying after a timeout, can never charge twice. Unknown outcomes are resolved by reconciliation, not guessed. |
| 6 | The domain has no idea webhooks exist. Duplicate and out-of-order webhooks leave the payment in the correct state. |
| 7 | You can defend each boundary in an ADR, and a 4th provider is added by writing an adapter + config only. |
| 8 | You can explain where your Level-1 interface leaks, and you choose a capability model deliberately, not by accident. |

---

## 8. Glossary of patterns (learn the idea, then the name)

| Name | One-line idea | Appears in |
|------|---------------|-----------|
| Dependency Inversion | High-level policy depends on abstractions it owns; details implement them | L1 |
| Ports & Adapters / Hexagonal | The app defines ports; the outside world plugs in adapters | L1–L2 |
| Adapter | Translate one interface/model into another | L2 |
| Anti-Corruption Layer | A translation boundary that keeps a foreign model out of yours | L2, L6 |
| Strategy | Swap an algorithm (e.g. routing rule) behind an interface | L3 |
| Registry vs Factory | Look up an existing instance by key vs construct a new one | L3 |
| Chain of Responsibility / Rule list | Ordered rules, first match wins | L3 |
| Decorator | Wrap a port implementation to add retry/timeout/logging without changing it | L4 |
| Circuit Breaker | Stop calling a failing dependency for a while | L4 |
| Idempotency Key | Same request id → same effect, at most once | L5 |
| State Machine | Explicit allowed transitions between payment states | L5 |
| Reconciliation | Periodically ask the provider for the truth and repair our state | L5 |
| Inbox / Outbox | Durably record incoming/outgoing messages to process exactly-once-in-effect | L6, L7 |
| Interface Segregation | Many small capability interfaces beat one fat one | L8 |

---

## 9. Changes from the original interview script

The first version of this file was an **interviewer's script**. It has been turned
into a **learner's roadmap** and corrected in a few places:

1. **Level 0 added.** You build the naive version on purpose so the later levels
   have a pain to cure.
2. **Old Question 4 split into L4 (failures & fallback) and L5 (idempotency &
   unknown outcomes).** They are different problems; the second one is the
   hardest idea in the whole path and deserves its own demo.
3. **Corrected the fallback guidance.** The original suggested a timeout could
   trigger fallback to another provider. A timeout means *outcome unknown*:
   falling back can **double-charge** the customer. Only errors that prove no
   charge happened are safe to fall back on.
4. **Corrected the "adapter absorbs PayPal's two steps" claim.** The server-side
   create→capture can be absorbed. **Buyer approval cannot**: the customer must be
   redirected. The result model needs a `RequiresAction` outcome (the same applies
   to 3-D Secure on Stripe/Adyen).
5. **Routing precedence and conflicts made explicit** (L3). Example: the merchant
   is configured for PayPal but pays in a currency PayPal doesn't support.
   "Stripe unavailable → Adyen" moved to L4, since detecting unavailability is a
   failure-handling concern.
6. **Webhook security and ordering added** (L6): signature verification,
   duplicates, out-of-order delivery, webhook-vs-reconciliation races.
7. **Port ownership convention fixed**: ports live in `application/ports/`, the
   domain stays free of any I/O concept.
8. **Testing strategy, simulators, acceptance criteria and a tech-stack decision
   table added**, so each level is buildable, not just discussable.
