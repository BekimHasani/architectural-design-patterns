# ADR-0005: Capability model

**Status:** accepted. Replaces the single `PaymentGateway` port the platform started with.

## Context

Providers differ: some can't refund, some can't hold funds, some only answer later, some take two
currencies. With that single port, Provider X had three methods answering `UNSUPPORTED`, and the contract test
skipped what didn't apply. Callers couldn't trust the interface.

## Decision

Both a code model and a data model, kept honest by checks:

| Part | Answers | Example |
|------|---------|---------|
| Capability interfaces | What can code call? | `Authorizer`, `DirectCharger`, `Capturer`, `Canceller`, `Refunder`, `ActionCompleter` |
| `ProviderDescriptor` | Who should we ask? | operations, currencies, refund terms, confirmation style |
| `InMemoryGatewayRegistry` | Do the two agree? | refuses to start if they don't |
| `GatewayContractTest` | Does the code do what it declares? | generates tests from the descriptor |

`ProviderAdapter` is the only mandatory part: a descriptor and `getStatus`, because reconciliation
can't work without it.

Consequences for the rest of the design:

- **The domain states intent, not provider abilities.** `CaptureMode.MANUAL` means hold;
  `AUTOMATIC` means take the money now, by direct charge or authorize + capture.
- **The router filters before choosing.** A payment never reaches a provider that can't serve it.
- **Unsupported operations are refused in our words**, from the descriptor, before anything is
  stored or sent. Responses list `allowedActions`.
- **Non-boolean capabilities are data.** `RefundTerms` ("once, in full") is enforced by the aggregate.
- **Resilience wraps the call, not the interface.** A decorator would need one version per
  combination, so `CallPolicy` wraps every call inside `GatewayRegistry.call`.
- **Providers register themselves** through a `ProviderFactory`. The composition root names none.

## Rejected

| Option | Why not |
|--------|---------|
| Fat interface + `NotSupported` | Lies about capabilities; failures appear at runtime, deep inside |
| Interfaces only | The router would need `instanceof` chains, and currencies and terms aren't types |
| Descriptors only | Nothing stops a provider declaring what it can't do |
| Lowest common denominator | Only "charge" would survive: no holds, no partial refunds |

## Costs

- More types, and a cast inside the registry: `Capability`, its interface and the descriptor must agree.
- `RefundTerms` covers partial and count limits. A time window (e.g. 90 days) needs a capture date
  on the operation; it is not built.
