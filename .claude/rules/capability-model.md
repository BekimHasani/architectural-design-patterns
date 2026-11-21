---
paths:
  - "levels/08-*/src/**/providers/**"
  - "levels/08-*/src/**/payments/**"
---

# Level 8 capability model (option D, see `docs/adr/0005-capability-model.md`)

A provider makes two claims, and `InMemoryGatewayRegistry.requireHonestDescriptor` checks at startup that they agree (if they don't, the app won't start):
- **Types**: it implements `ProviderAdapter` plus only the capability interfaces it really supports (`Authorizer`, `DirectCharger`, `Capturer`, `Canceller`, `Refunder`, `ActionCompleter`) from `payments/application/capabilities`.
- **Data**: a `ProviderDescriptor` (capabilities, currencies, refund terms, sync/async confirmation). `ProviderRouter` filters candidates on it **before** choosing (`canTake(currency, captureMode, alsoRequired)`).

Every outbound call goes through `GatewayRegistry.call(providerId, Capability.class, gw -> …)`, which applies breaker + retry from `platform/resilience`. An unsupported operation must produce a domain-level result (HTTP 422 before any call), never a runtime "not supported" thrown from inside an adapter.

`GatewayContractTest` is the shared TCK. Each provider's test extends it, and the kit generates tests from the declared descriptor. Adding a provider follows `docs/ADDING-A-PROVIDER.md`: new `providers/<name>/` folder, factory, contract test and `application.yml` entries only, with no changes to `payments/`, `api/`, `platform/` or `config/`.