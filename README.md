# Design Patterns & Architecture — Learning Demos

Small, runnable demo projects that each teach one architectural idea, built
around a single evolving problem: **a checkout that charges customers through
multiple payment providers**.

- **Start here:** [Architecture.md](Architecture.md), the single source of truth
  (level map, conventions, shared domain, glossary).
- **Levels:** [levels/](levels/), one folder per level, from easiest to hardest.
- **Fake providers:** [tools/provider-simulators/](tools/provider-simulators/README.md)

## Build

Java 21 + Maven. Use the included wrapper; no Maven install needed.

```bash
./mvnw test      # builds the simulators and every level, runs all tests
```

See [Architecture.md §5.2.1](Architecture.md#521-build-and-run) to run a single level.

## Levels

| # | Level | Status |
|---|-------|--------|
| 0 | [Naive checkout](levels/00-naive-checkout/README.md) | ✅ done |
| 1 | [Provider abstraction](levels/01-provider-abstraction/README.md) | ✅ done |
| 2 | [Adapters & mapping](levels/02-adapters-and-mapping/README.md) | ✅ done |
| 3 | [Provider routing](levels/03-provider-routing/README.md) | ✅ done |
| 4 | [Failures & fallback](levels/04-failures-and-fallback/README.md) | ✅ done |
| 5 | [Idempotency & unknown outcomes](levels/05-idempotency-and-unknown-outcomes/README.md) | ✅ done |
| 6 | [Webhooks & async state](levels/06-webhooks-and-async-state/README.md) | ✅ done |
| 7 | [Full payment platform](levels/07-full-payment-platform/README.md) | ✅ done |
| 8 | [Scaling to 20 providers](levels/08-hexagonal-architecture/README.md) | ✅ done |

Status legend: ⬜ not started · 📝 designed (NOTES.md written) · 🛠 implementing · ✅ done
