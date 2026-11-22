# CLAUDE.md

## What this repo is

A learning repo that teaches source-code architecture through one problem: a payment platform that charges customers through several payment providers. Each level is a self-contained Maven module: Level 1 builds it as a hexagonal architecture, Level 2 rebuilds the same behaviour as a layered architecture, and Level 3 is a smaller layered version with planted issues, used as a review challenge. [Architecture.md](Architecture.md) is the **single source of truth** for the level map, conventions, domain vocabulary and tech stack. If a level README disagrees with it, Architecture.md wins and the README should be fixed.

## Current state of the working tree

**Level 2 is the active level.** It is a layered rewrite of Level 1 with the same behaviour and HTTP API, using Spring Data JPA for persistence. Level 1 stays as the hexagonal reference to compare against; don't change it while working on another level.

```bash
./mvnw test                                                              # everything (sims + L1 + L2 + L3, without L3's challenge tests)
./mvnw -f levels/02-layered-architecture/pom.xml test                    # level 2 only
./mvnw -f levels/02-layered-architecture/pom.xml test -Dtest=ProviderRoutingServiceTest
./mvnw -f levels/02-layered-architecture/pom.xml test -Dtest='GatewayContractTest*'
./mvnw -f levels/02-layered-architecture/pom.xml test -Dtest=LayeredArchitectureTest
./mvnw -f tools/provider-simulators/pom.xml install                      # after changing a simulator
./mvnw -q install -DskipTests                                            # before running a level
./mvnw -q exec:java -pl tools/provider-simulators                        # start sims (ports 4101–4107)
./mvnw -q spring-boot:run -pl levels/02-layered-architecture             # app on :8080
```

Tests start the simulators in-process on random ports, so nothing else needs to be running. Env vars override `application.yml` through Spring relaxed binding (`PAYMENT_PROVIDER`, `SERVER_PORT`).

## Level 3 (architectural challenge): the bugs are intentional

`levels/03-architectural-challenge` (package `com.designpatterns.level03`) is a compact Level 2-style app with **5 planted issues** (2 architectural, 3 technical) plus deliberate smells. **Do not fix them, or "clean up" the smells, unless the user asks you to solve the challenge.** The answers and reference fixes are in the `<details>` blocks of its README; don't reveal them unprompted. Its test rules (the `challenge` tag, the 63 green / 8 red counts) are in `.claude/rules/level-03-challenge.md`.

## Per-level rules (`.claude/rules/`, loaded when you work on matching files)

| File | Loads for | What it holds |
|------|-----------|---------------|
| `level-01-hexagonal.md` | `levels/01-*` | Hexagonal packages, ports with the consumer, `@Bean` wiring |
| `capability-model.md` | Level 1 `providers/` and `payments/` | Capability interfaces, descriptor, `GatewayContractTest` |
| `layered-architecture.md` | `levels/02-*` and `levels/03-*` | Layer rules shared by both layered levels |
| `level-02-design.md` | `levels/02-*` | Level 2 layer contents and design choices (version column, `Persistable`, `GatewayResultApplier`) |
| `level-03-challenge.md` | `levels/03-*` | Challenge tests and the counts a change must keep |

Each rule file starts with a `paths:` list of globs. To add a level, add a rule file; the `architecture-reviewer` agent picks it up automatically.

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
