# Design Patterns & Architecture — Learning Demos

Runnable versions of the same system, built to teach source-code architecture:
**a payment platform that charges customers through multiple payment providers**.
Levels 1 and 2 have identical behaviour and differ only in architecture; Level 3 is a
flawed version to review and fix.

- **Start here:** [Architecture.md](Architecture.md), the single source of truth
  (level map, conventions, shared domain, glossary).
- **Levels:** [levels/](levels/), one folder per level.
- **Fake providers:** [tools/provider-simulators/](tools/provider-simulators/README.md)

## Build

Java 21 + Maven. Use the included wrapper; no Maven install needed.

```bash
./mvnw test      # builds the simulators and every level, runs all tests (except Level 3's challenge tests)
```

See [Architecture.md §5.3](Architecture.md#53-build-and-run) to run a single level.

## Levels

| # | Level | Status |
|---|-------|--------|
| 1 | [Hexagonal architecture: scaling to 20 providers](levels/01-hexagonal-architecture/README.md) | ✅ done |
| 2 | [Layered architecture (same platform, Spring Data JPA)](levels/02-layered-architecture/docs/CODE-GUIDE.md) | ✅ done |
| 3 | [Architectural challenge: review a flawed layered platform](levels/03-architectural-challenge/README.md) | 🧩 challenge |
