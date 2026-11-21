# Level 1 — Hexagonal Architecture: Scaling to 20 Providers

> **New pressure:** 20 providers with different capabilities.
> **Concept:** Leaky abstractions, interface segregation, capability modelling, and making extension safe at scale.
> **Starts from:** a payment platform (authorize / capture / refund / audit / many merchants) built on one `PaymentProvider` port.
> **Next:** [Level 2](../02-layered-architecture/docs/CODE-GUIDE.md) rewrites this same platform as a layered architecture.

## Scenario

Six months later the company has **20 payment providers**. Each one differs a
little:

- some support refunds, some don't,
- some support authorization/capture, some only immediate payment,
- some support recurring payments,
- some have asynchronous confirmation only,
- some don't support certain currencies,
- some allow partial refunds only once, or only within 90 days.

## The question

> **Does your original abstraction still work?**

The original one-size-fits-all port:

```java
interface PaymentProvider {
    GatewayResult charge(…);
    GatewayResult refund(…);
}
```

looked clean, but now half the implementations answer `UNSUPPORTED`. That's a
leaky abstraction and a Liskov violation: callers can't trust the interface.

## Questions (answer in NOTES.md before coding)

1. One large interface or several small ones? Why?
2. How does the **router** know a provider can't refund EUR before it chooses it?
3. Where is "capability" information: in code (types), in data (descriptors), or
   both?
4. How do you avoid the **lowest-common-denominator trap** (only modelling what
   all 20 can do) **and** the **superset trap** (a god interface)?
5. How do you guarantee all 20 adapters behave consistently? Who writes the tests?
6. What does onboarding provider #21 look like, step by step? Can a different
   team do it without touching `payments/`?
7. A refund is requested for a payment whose provider doesn't support refunds.
   What happens: domain error, alternative flow (manual payout), or something
   prevented upstream?

## Options to compare (write the trade-offs)

| Option | Shape | Pros | Cons |
|--------|-------|------|------|
| A. Fat interface + `NotSupported` | one interface, throw at runtime | simple | lies about capabilities; runtime failures |
| B. Segregated interfaces | `Authorizer`, `Capturer`, `Refunder`, `RecurringBiller`, `WebhookSource`; adapter implements a subset; type guards | compile-time honesty | discovery needs guards / registry per capability |
| C. Capability descriptor | each adapter exposes data: `{ operations, currencies, partialRefund, async, limits }`; router filters on it | data-driven routing, easy to inspect | must keep descriptor and code in sync, so you need contract tests |
| D. B + C | interfaces for **what code can call**, descriptor for **routing constraints** | best of both | more moving parts |

## Concepts to study

- Interface Segregation Principle, Liskov Substitution Principle
- Leaky abstractions
- Plugin architecture; provider "SDK" for internal teams
- Technology Compatibility Kit (TCK): one shared contract-test suite every
  adapter must pass, parameterised by its declared capabilities
- Feature flags / per-merchant enablement of providers

## Acceptance criteria for the demo

You don't need 20 real adapters. Add **3 small synthetic providers** to the
simulators with deliberately awkward capabilities (no refunds; charge-only;
async-only + 2 currencies).

- [ ] Capability model implemented (option of your choice, justified in an ADR).
- [ ] Router filters candidates by required capability + currency **before**
      selecting; tested.
- [ ] Requesting an unsupported operation yields a clear domain-level result,
      never a runtime `NotSupported` from deep inside an adapter.
- [ ] A shared provider contract-test kit runs against all adapters, enabling
      test groups based on declared capabilities.
- [ ] Adding a synthetic provider touches only its own folder + registration.
- [ ] A short write-up: "Where my original abstraction leaked, and what I changed."

## Reference design

<details>
<summary>Open only after writing NOTES.md</summary>

Implemented in [src/](src/main/java/com/designpatterns/level01/). This is option **D**:
read [ADR-0005](docs/adr/0005-capability-model.md),
[where the abstraction leaked](docs/WHERE-THE-ABSTRACTION-LEAKED.md) and
[adding a provider](docs/ADDING-A-PROVIDER.md).

```java
// payments/application/capabilities: small, honest interfaces
public interface Capturer { GatewayResult capture(OperationRequest request); }
public interface Refunder { GatewayResult refund(OperationRequest request); }

// the only mandatory part
public interface ProviderAdapter {
    ProviderDescriptor descriptor();
    GatewayResult getStatus(String attemptId, ProviderCredentials credentials);
}

// a provider implements what it really has, and nothing else
public final class AsyncPayGateway implements ProviderAdapter, DirectCharger, Refunder { … }

// every call, for any capability, passes the same resilience policy
gateways.call(providerId, Refunder.class, gateway -> gateway.refund(request));
```

```
AuthorizePayment ──► ProviderRouter ── descriptors: currency? capture mode? required capabilities?
       │
       └──► PaymentOrchestrator / PaymentOperations ──► GatewayRegistry.call(id, Capability, …)
                                                              │ breaker( retry( call ) )
                                                              ▼
                                                  Capturer / Refunder / … in providers/<name>/
```

| Provider | Authorize | Charge | Capture | Cancel | Refund | Customer step | Currencies | Results |
|----------|-----------|--------|---------|--------|--------|---------------|------------|---------|
| stripe | ✅ | | ✅ | ✅ | ✅ | ✅ | EUR USD GBP JPY | sync or async |
| paypal | ✅ | | ✅ | ✅ | ✅ | ✅ | EUR USD GBP | sync |
| adyen | ✅ | | ✅ | ✅ | ✅ | ✅ | EUR USD GBP JPY | sync or async |
| providerx | | ✅ | | | ✅ | | EUR USD | sync |
| holdonly | ✅ | | ✅ | ✅ | | | EUR USD | sync |
| chargeonly | | ✅ | | | | | EUR USD | sync |
| asyncpay | | ✅ | | | once, in full | | EUR SEK | async |

**Answers to the questions:**

| # | Question | Answer here |
|---|----------|-------------|
| 1 | One interface or several? | Several: a caller should never hold a method that may not work |
| 2 | How does the router know? | `ProviderDescriptor.canTake(currency, captureMode, alsoRequired)`, before choosing |
| 3 | Code or data? | Both, checked against each other at startup and by the contract kit |
| 4 | LCD and superset traps | The domain states intent (`CaptureMode`); providers declare abilities |
| 5 | Consistency across 20 adapters | One kit, tests generated from the descriptor, written once by the platform team |
| 6 | Provider #21 | [Seven steps](docs/ADDING-A-PROVIDER.md), none in the core |
| 7 | Refund at a provider that can't | Both: `refundsRequired` routes around it up front; otherwise 422 before any call, and `allowedActions` shows it. A manual payout stays outside the platform |

**How each acceptance criterion is proven:**

| Criterion | Proof |
|-----------|-------|
| Capability model + ADR | `payments/application/capabilities/`; ADR-0005 |
| Router filters first | `ProviderRouterTest.aProviderThatCantDoWhatThePaymentNeedsIsNotACandidate`; e2e `askingForRefundsUpFront…` |
| Clear result for unsupported operations | `PaymentOperationsTest.aRefundAtAProviderThatCantRefund…`; e2e `noRefundProvider…` |
| Contract kit over all adapters | `GatewayContractTest` × 7: 18 tests for Stripe, 14 for asyncpay, 11 for chargeonly, 0 skipped |
| Honest descriptors | `InMemoryGatewayRegistry.requireHonestDescriptor`: declaring REFUND on chargeonly stops the app |
| Own folder + registration only | Measured with `diff` against a snapshot: **0 lines of existing Java** changed; `application.yml` and new tests only. The shared `SyntheticClient` went in first, like a vendor SDK |
| Write-up | [docs/WHERE-THE-ABSTRACTION-LEAKED.md](docs/WHERE-THE-ABSTRACTION-LEAKED.md) |

**Not built:** a refund time window (it needs a capture date on the operation), and webhooks for the
synthetic providers (they resolve through reconciliation).

**A concurrency bug fixed here:** loading a payment could mix a current version with an older list
of operations, so racing refunds could over-reserve. Measured: 12 failures in 400 runs before the
snapshot read, 0 in 800 after.

</details>

## Run it

```bash
./mvnw test -pl levels/01-hexagonal-architecture -am      # from the repo root

./mvnw -q install -DskipTests
./mvnw -q exec:java -pl tools/provider-simulators                     # terminal 1
./mvnw -q spring-boot:run -pl levels/01-hexagonal-architecture     # terminal 2

api() { curl -s "localhost:8080$2" -H "X-Merchant-Id: $1" -H "Idempotency-Key: $(uuidgen)" -H 'Content-Type: application/json' "${@:3}"; echo; }
api hooli /payments -d '{"customerId":"c-1","currency":"USD","amountMinor":1000,"capture":"automatic"}'
# provider holdonly, captured, "allowedActions":[]
api hooli /payments -d '{"customerId":"c-1","currency":"USD","amountMinor":1000,"refundsRequired":true}'
# provider stripe: the merchant's rule is skipped because holdonly can't refund
api pied-piper /payments -d '{"customerId":"c-1","currency":"USD","amountMinor":1000}'
# 422: nobody this merchant can use is able to hold funds
```

## Where to go after this

[Level 2](../02-layered-architecture/docs/CODE-GUIDE.md) keeps this behaviour and HTTP API and rebuilds
it as a classic layered architecture with Spring Data JPA, so you can compare who owns which interface.

Ideas for extra levels after that:

- Recurring payments / subscriptions (scheduling, dunning)
- Disputes & chargebacks (long-running, externally driven workflow, i.e. sagas)
- Splitting the modular monolith into services: which boundary would you cut
  first, and what does it cost?
- Event sourcing the `PaymentTest` aggregate and comparing with the audit-table approach
