---
paths:
  - "levels/02-*/**"
---

# Level 2 (layered architecture): what goes beyond the shared layer rules

The shared layer rules are in `layered-architecture.md`. The target design is in `levels/02-layered-architecture/docs/LAYERED-ARCHITECTURE-PROPOSAL.md`, and the reading guide in `docs/CODE-GUIDE.md`. The code carries `FLOW A–D, S` step comments and a `package-info.java` guide per layer; keep them accurate when changing a flow.

What each layer holds in Level 2:
- `model/`: `Payment` aggregate, `Money`, ids, `Capability`, domain exceptions including `ConcurrentUpdateException`.
- `persistence/`: `PaymentRepository`, `PaymentAttemptRepository`, `ProviderEventInboxRepository`, `IdempotencyKeyRepository`, `AuditEventRepository`, `MerchantConfigRepository`.
- `integration/`: `gateway/` (`ProviderAdapter`, capability interfaces, `CapabilityBinding`, sealed `GatewayResult`, the `ProviderGateway` facade, `ProviderFactory`), `resilience/` (retry + circuit breaker), `support/`, `webhook/` (`ProviderEventTranslator` strategy, `WebhookTranslators` registry), `provider/<name>/`.
- `service/`: payment, routing, event, reconciliation, merchant, idempotency, audit. Transactions are opened only inside persistence, with `TransactionTemplate`.
- `presentation/`: `rest/` (incl. `IdempotentRequestExecutor`), `webhook/ProviderWebhookController` (one `POST /webhooks/{provider}` for every provider), `scheduling/ReconciliationScheduler`.
- `config/`: providers register themselves through a `ProviderFactory` `@Component`, so config never names one.

Design choices to keep:
- A payment's `version` is a plain column, not JPA `@Version`. `JpaPaymentRepository` locks the row (`SELECT … FOR UPDATE`), checks the version and writes it +1 in one short transaction. A lost race or a lock timeout becomes `ConcurrentUpdateException`, and services reload, apply and save again.
- Insert-only rows (new payments, inbox events, idempotency keys) implement `Persistable`, so `save()` inserts and fails on a duplicate instead of silently overwriting.
- How a `GatewayResult` changes a `Payment` is decided only in `service/payment/GatewayResultApplier`.

Level 2 uses a file H2 DB at `./target/level02-db`.
