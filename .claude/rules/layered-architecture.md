---
paths:
  - "levels/02-*/**"
  - "levels/03-*/**"
---

# Layered architecture (Level 2, and the baseline Level 3 is reviewed against)

Each layered level enforces these rules with its own `architecture/LayeredArchitectureTest`; a violating import fails the build. Packages are `com.designpatterns.level02` and `com.designpatterns.level03`. Dependencies only point down:

```
presentation → service → persistence | integration → model        (config wires everything)
```

- `model/`: the domain. **Pure Java**: depends only on itself and `java.*`. No `@Entity`, no Spring.
- `persistence/`: repository interfaces owned by this layer, written in model types. `persistence/jpa/` holds the package-private `@Entity` classes, Spring Data interfaces (one top-level file each; Spring Data doesn't scan nested ones) and `Jpa*Repository` adapters that map entity ↔ model. **JPA/Hibernate may only be used in `persistence.jpa`.**
- `integration/`: the provider contract (`gateway/`), shared HTTP plumbing (`support/`) and one adapter per provider in `provider/<name>/`.
- `service/`: `@Service` use cases. Services may not use Spring Data/JPA/web types, may not reference a concrete provider, and may not use **`@Transactional`**: a transaction must never stay open across a provider call.
- `presentation/`: controllers, DTOs and the `ApiExceptionHandler` (ProblemDetail). Controllers live only here, and no layer below may depend on it.
- `config/`: the composition root and the only place that reads configuration. It must **not name any provider**.
- `persistence` and `integration` must not depend on each other.
- Each `integration/provider/<name>/` exposes only its gateway as public. DTOs, mappers, clients and webhook translators are package-private. Providers may not depend on each other (except through `integration/support`).

`schema.sql` owns the tables; Hibernate runs with `ddl-auto: none` and `open-in-view: false`.

**In Level 3, code that breaks these rules may be a planted issue.** Follow the Level 3 rule in the root `CLAUDE.md`: don't fix it unless the user asks you to solve the challenge.
