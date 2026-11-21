# Level 3 — Architectural Challenge: Review a Layered Payment Platform

> **New pressure:** a teammate rebuilt a smaller version of the Level 2 platform. It compiles, the
> build is green, and the demo works. You are the reviewer.
> **Concepts:** code review against architecture rules, fitness functions, finding bugs with tests.
> **Starts from:** the layered rules of [Level 2](../02-layered-architecture/docs/CODE-GUIDE.md).

## Scenario

The platform charges a customer through one of two providers, and can refund part or all of a payment
later. It follows the Level 2 layering:

```
presentation → service → persistence | integration → model        (config wires everything)
```

| Endpoint | What it does |
|----------|--------------|
| `POST /payments` | Charges `{customerId, currency, amountMinor}`. Needs `X-Merchant-Id` and `Idempotency-Key`. |
| `GET /payments/{id}` | Reads a payment. Only its own merchant can see it. |
| `POST /payments/{id}/refunds` | Refunds `{amountMinor, reason}` at the provider that charged. Needs `Idempotency-Key`. |

- **Providers:** the Stripe-like and Adyen-like simulators (`integration/provider/stripe`, `.../adyen`).
- **Routing:** EUR prefers Adyen, everything else tries Stripe then Adyen (`application.yml`).
- **Fallback:** if a provider fails, the next one is tried.
- **Storage:** H2 through Spring Data JPA (`persistence/jpa`), tables owned by `schema.sql`.

```
src/main/java/com/designpatterns/level03/
├── presentation/rest/      PaymentController, ApiExceptionHandler, dto/
├── service/                payment/PaymentService, routing/RoutingService, idempotency/IdempotencyService
├── persistence/            PaymentRepository, IdempotencyRepository + jpa/ adapters
├── integration/            gateway/ (PaymentGateway, GatewayResult, ProviderGateways), support/, provider/
├── model/                  Payment, Money, PaymentStatus, exceptions/
└── config/                 PlatformConfig, RoutingProperties
```

## The challenge

The code contains **5 planted issues**:

- **2 architectural issues:** code that breaks a Level 2 layering rule. The app still works today;
  the cost shows up at the next change.
- **3 technical issues:** wrong behaviour that a unit test can catch. Each one can cost real money
  or leak data.

On top of those, the code has **inconvenient code** (smells that make the issues easy to miss) and
**room for improvement** (things a production system would need). These are not counted as issues,
but a good review mentions them.

### Steps

1. **Review the code without running the challenge tests.** For each issue write down in `NOTES.md`:
   where it is, which rule or invariant it breaks, a concrete failure scenario, and your fix.
2. **List the smells and improvements** you would raise in the review.
3. **Check your findings** with the challenge tests:
   ```bash
   ./mvnw -f levels/03-architectural-challenge/pom.xml test -Pchallenge
   ```
   They are tagged `challenge`, are excluded from the normal build, and **8 of them fail** until the
   issues are fixed.
4. **Fix the code without changing any test.** You're done when both commands are green:
   ```bash
   ./mvnw -f levels/03-architectural-challenge/pom.xml test               # 63 tests, green from the start
   ./mvnw -f levels/03-architectural-challenge/pom.xml test -Pchallenge   # 8 tests, green once fixed
   ```
5. **Compare** with the reference answers below.

### Acceptance criteria

- [ ] `NOTES.md` names all 5 issues with a failure scenario each, before you ran `-Pchallenge`.
- [ ] Both builds above are green, and no test file was changed.
- [ ] The fix for each architectural issue removes the dependency; it doesn't hide it (no
      `ignoreDependency`, no moving classes into another package to fool the rule).
- [ ] At least 5 smells or improvements are listed, each with the change you would make.

<details>
<summary>Hints (one per issue, open only if stuck)</summary>

1. Draw the arrows between the imports of `PaymentService`. Do they all point down?
2. Look at every `instanceof` in the service layer. What does each one know that it shouldn't?
3. Refund 60.00 of a 100.00 payment. Then try to refund 60.00 again.
4. `GatewayResult.Failed` has a `kind`. Who reads it?
5. `IdempotencyService.execute` takes four parameters. Which ones does it use?

</details>

<details>
<summary>Reference answers (open only after writing NOTES.md)</summary>

### The 5 issues

| # | Kind | Where | What's wrong | What can happen | Detected by |
|---|------|-------|--------------|-----------------|-------------|
| 1 | Architectural | `service/payment/PaymentService.find` | Returns `presentation.rest.dto.PaymentResponse`. The service layer depends on the layer **above** it. | Renaming a JSON field means changing a use case. A second entry point (a CLI, a message consumer, the scheduler) has to accept HTTP DTOs. | `LayeredArchitectureTest.layersOnlyCallDown` |
| 2 | Architectural | `service/payment/PaymentService.refund` | `instanceof StripeGateway` and a Stripe-only `refund(request, reason)`. Business code knows a concrete provider and bypasses the `PaymentGateway` contract. | Adyen silently drops the refund reason. The next provider that wants it adds another `instanceof`, and the service grows a branch per provider. | `LayeredArchitectureTest.servicesSeeNoConcreteProvider` |
| 3 | Technical | `model/Payment.checkRefundable` | Compares the refund with the **original amount**, not with what is **left** after earlier refunds. | 60 + 60 on a 100.00 payment passes our check. Our state says 120.00 refunded, and we rely on the provider to refuse the money we should never have asked for. | `PaymentTest.refundsTogetherCanNeverExceedTheCapturedAmount` |
| 4 | Technical | `service/payment/PaymentService.charge` | Every `Failed` result falls back to the next provider, including `OUTCOME_UNKNOWN` (timeout, connection cut after sending). | Stripe charges, the response is lost, Adyen charges again: the customer pays twice, and our record shows only Adyen. | `PaymentServiceTest.aLostResponseIsNeverRetriedAtAnotherProvider`, `PaymentApiTest.aLostResponseNeverChargesTheCustomerTwice` |
| 5 | Technical | `service/idempotency/IdempotencyService.execute` | The key is stored alone. `merchantId` and `operation` are accepted and ignored. | Globex reuses a key Acme used ("order-42"): Globex gets Acme's payment id, then a 404, and is **not charged**. A refund sent with a key already used for a charge returns the charge's id and **refunds nothing**. | `IdempotencyServiceTest.theSameKey…` (2 tests), `PaymentApiTest.twoMerchantsCanUseTheSameIdempotencyKey` |

### Reference fixes

1. `PaymentService.find` returns the `Payment` model; the controller maps it with
   `PaymentResponse.from(...)`.
2. Put the reason in the contract: `RefundRequest(operationId, providerReference, amount, reason)`.
   `StripeGateway.refund(RefundRequest)` sends it, Adyen ignores it, and the service calls
   `gateways.get(id).refund(request)` with no `instanceof`.
3. `if (requested.isGreaterThan(amount.minus(refunded)))`.
4. On `Failed` with `FailureKind.OUTCOME_UNKNOWN`: `payment.markOutcomeUnknown(providerId, detail)`,
   save, and **stop**. Only `NOTHING_CHARGED` may fall back.
5. Store under `merchantId + "|" + operation + "|" + idempotencyKey`.

All five together make the 8 challenge tests pass and keep the 63 normal tests green, with no
test changes.

### Inconvenient code (smells)

| Smell | Where | Why it hurts | Better |
|-------|-------|--------------|--------|
| `if / else if instanceof` on a sealed type | `PaymentService.charge` | Not exhaustive and ignores `kind`: this is how issue 4 hid. | A pattern-matching `switch` over `GatewayResult`, with `Failed(NOTHING_CHARGED, …)` and `Failed(OUTCOME_UNKNOWN, …)` as separate cases. |
| `System.out.println` with string concatenation | `PaymentService.charge` | No levels, no correlation id, no attempt id in a searchable field. | SLF4J with MDC (Level 2's log pattern). |
| Same "load this merchant's payment or 404" code twice | `PaymentService.refund`, `find` | The next copy will forget the merchant filter. | One private `loadOwned(merchantId, paymentId)`. |
| Stringly-typed ids | `merchantId`, `paymentId`, `providerId` are all `String` | `refund(merchantId, paymentId, …)` compiles with the arguments swapped. | Value types like Level 2's `MerchantId`, `PaymentId`, `ProviderId`. |
| `Payment.restore` with 9 positional parameters | `model/Payment`, `JpaPaymentRepository` | Several `String`s in a row are easy to swap. | Value types (above), or a small builder used by persistence. |
| Hand-written validation, repeated | `PaymentController.create`, `refund` | Duplicated checks and messages. | Bean Validation (`@Valid`, `@NotBlank`, `@Positive`) on the request records. |
| The payment is read twice per refund | `PaymentController.refund` | An extra query, and the currency comes from the DTO. | Let the service work out the currency. |
| Config read inside adapters with `@Value` | `StripeGateway`, `AdyenGateway` | Configuration is spread across the code, against the "config is read in `config/` only" rule. | `@ConfigurationProperties` records in `config/`, passed to the adapters. |
| Hard-coded timeouts | `ProviderHttp.restClient` (1 s / 2 s) | Can't be tuned per provider or per environment. | Configuration, as in Level 2's `payment.resilience`. |
| A provider error reported as a business-rule violation | `PaymentService.refund` | A 503 at Stripe becomes HTTP 422 "rule violation". An unknown refund outcome is just thrown away. | A typed result or a dedicated exception mapped to 502/503, and an `OUTCOME_UNKNOWN` refund state. |
| Inconsistent imports | `StripeGateway` uses the fully-qualified `ProviderHttp` name; `AdyenGateway` imports it | Noise. | Import it. |

### Room for improvement

- **Idempotency race:** "find, then run, then save" lets two concurrent requests with the same key
  both run. Claim the key with an insert first (unique key, `Persistable` as in Level 2), then do
  the work.
- **Idempotency ignores the request body:** the same key with a different amount silently returns
  the first payment. Store a hash of the request and answer 422 on a mismatch.
- **Concurrent refunds:** there is no version on `payments`, so two refunds can both pass
  `checkRefundable` and one write overwrites the other. Lock the row or check a version (Level 2's
  `JpaPaymentRepository`).
- **Unknown outcomes are never resolved:** after fixing issue 4, `OUTCOME_UNKNOWN` payments stay
  that way. The attempt id isn't stored, so reconciliation can't look the payment up at the
  provider. Store attempts and add a reconciliation job (Level 2's `ReconciliationService`).
- **No write-ahead for refunds:** a crash after the provider refunded but before `save` loses the
  refund. Store an in-flight refund first.
- **Routing ignores what a provider supports:** currencies and refunds are not checked before
  routing (Level 1's capability model).
- **No retry or circuit breaker** around provider calls.

</details>

## Run it

```bash
./mvnw -q install -DskipTests                                              # once, from the repo root
./mvnw -q exec:java -pl tools/provider-simulators                          # terminal 1
./mvnw -q spring-boot:run -pl levels/03-architectural-challenge            # terminal 2, app on :8080

curl -s localhost:8080/payments -H 'X-Merchant-Id: acme' -H "Idempotency-Key: $(uuidgen)" \
     -H 'Content-Type: application/json' -d '{"customerId":"c-1","currency":"USD","amountMinor":1999}'
```

## Tests

| Package | Kind | What it covers |
|---------|------|----------------|
| `model/` | Plain JUnit | `Money`, `Payment` state and refund rules |
| `service/` | Plain JUnit with in-memory fakes (`support/`) | Charging, fallback, refunds, routing, idempotency |
| `integration/provider/*` | Adapter tests against the simulators | Success, decline, 503, lost response, refund |
| `persistence/jpa/` | `@DataJpaTest` | Round trip through `schema.sql` |
| `architecture/` | ArchUnit | The Level 2 layering rules |
| `e2e/` | `@SpringBootTest` + simulators | HTTP in → providers; checks what really moved in the simulators' ledgers |

Tests tagged `challenge` detect the planted issues and run only with `-Pchallenge`.
