# Design Patterns & Architecture — Learning Demos

Two runnable versions of the same system, built to teach source-code architecture:
**a payment platform that charges customers through multiple payment providers**.
The behaviour and HTTP API are identical; only the architecture differs.

- **Start here:** [Architecture.md](Architecture.md), the single source of truth
  (level map, conventions, shared domain, glossary).
- **Levels:** [levels/](levels/), one folder per level.
- **Fake providers:** [tools/provider-simulators/](tools/provider-simulators/README.md)

## Build

Java 21 + Maven. Use the included wrapper; no Maven install needed.

```bash
./mvnw test      # builds the simulators and both levels, runs all tests
```

See [Architecture.md §5.3](Architecture.md#53-build-and-run) to run a single level.

## Levels

| # | Level | Status |
|---|-------|--------|
| 1 | [Hexagonal architecture: scaling to 20 providers](levels/01-hexagonal-architecture/README.md) | ✅ done |
| 2 | [Layered architecture (same platform, Spring Data JPA)](levels/02-layered-architecture/docs/CODE-GUIDE.md) | ✅ done |
