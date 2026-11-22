---
paths:
  - "levels/01-*/**"
---

# Level 1 (hexagonal reference)

Level 1 is the hexagonal reference that the other levels are compared against. Don't change it while working on another level.

Packages `payments` (framework-free core: `domain` + `application` with ports), `providers/*`, `platform`, `merchants`, `audit`, `api` and `config`, enforced by `architecture/ArchitectureTest`. Ports live with the consumer, and business classes are wired with `@Bean` methods, never `@Service`. Webhook controllers live in `providers/<name>/webhook/`, never in `payments`. The capability model is in `capability-model.md`.

```bash
./mvnw -f levels/01-hexagonal-architecture/pom.xml test
```

Level 1 uses a file H2 DB at `./target/level01-db`.
