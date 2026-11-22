---
paths:
  - "levels/03-*/**"
---

# Level 3 (architectural challenge): keeping the challenge intact

The rule not to fix the planted issues or reveal the answers is in the root `CLAUDE.md` and always applies. The layer rules the code is reviewed against are in `layered-architecture.md`.

Tests tagged `challenge` (JUnit `@Tag`, ArchUnit `@ArchTag`) detect the planted issues and fail until they are fixed. The level's `pom.xml` excludes them by default; `-Pchallenge` runs only them. Expected state: default build 63 green; `-Pchallenge` 8 failing. A change to Level 3 must keep both numbers, and the reference fixes in the README must still turn the 8 green without test changes.

```bash
./mvnw -f levels/03-architectural-challenge/pom.xml test               # 63 tests, green
./mvnw -f levels/03-architectural-challenge/pom.xml test -Pchallenge   # 8 tests, red by design
```

The learner's answers go in `levels/03-architectural-challenge/NOTES.md`. Level 3 uses a file H2 DB at `./target/level03-db`.
