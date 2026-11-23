# Level 3 as coded: how a refund works

> **⚠ Spoiler.** This page draws the refund path as the Level 3 code runs it today, including
> some planted issues. Read it after writing your `NOTES.md`, or if you've decided to look.

A refund gives back part or all of a captured payment, **at the provider that charged it**. Three
rules shape the flow:

- **Check before calling.** Business rules (is the payment refundable, is the amount allowed) are
  checked *before* any provider call, so a bad request never moves money.
- **Record only what the provider confirmed.** Our refunded total changes only after a `Succeeded`
  result.
- **One refund per client request.** The `Idempotency-Key` makes a retried request return the first
  result instead of refunding again.

`⚠` marks a step where the code breaks a layering rule or a domain invariant. Line numbers refer
to `src/main/java/com/designpatterns/level03/`.

---

## 1. The whole flow

`PaymentController.refund` (lines 65-79) → `IdempotencyService.execute` → `PaymentService.refund`
(lines 68-88) → `Payment` (lines 78-96) → a provider gateway.

```mermaid
flowchart TD
    A["POST /payments/:id/refunds<br/>headers: X-Merchant-Id, Idempotency-Key<br/>body: amountMinor, reason"] --> V{"amountMinor above zero?<br/>(Controller:69)"}
    V -- "no" --> E400a["400 Bad Request"]
    V -- "yes" --> F1{"find(merchant, id) (Controller:72)<br/>exists AND belongs to the caller?"}
    F1 -- "no" --> E404["404 Payment not found<br/>(we don't say which)"]
    F1 -- "yes" --> CUR["amount = amountMinor in the PAYMENT's currency (:73)<br/>the client can't pick a currency"]
    CUR --> K{"Idempotency-Key present?"}
    K -- "no" --> E400b["400 Idempotency-Key header is required"]
    K -- "yes" --> KH{"Key seen before?<br/>⚠ looked up by the key alone"}
    KH -- "yes" --> SKIP["Skip: no refund runs"]
    KH -- "no" --> S1["PaymentService.refund (:68)<br/>loads the payment again, merchant filter"]
    S1 --> CHK{"payment.checkRefundable(amount) (:72)<br/>see section 3"}
    CHK -- "rule broken" --> E422a["422 rule violation<br/>no provider call"]
    CHK -- "ok" --> REQ["RefundRequest(ref_ + new UUID, providerReference, amount) (:74)<br/>gateway = gateways.get(payment.providerId) (:75)"]
    REQ --> DISP{"⚠ gateway instanceof StripeGateway? (:77)"}
    DISP -- "yes" --> STR["stripe.refund(request, reason)<br/>POST /v1/refunds"]
    DISP -- "no (Adyen)" --> ADY["gateway.refund(request)<br/>POST /v71/payments/:psp/refunds<br/>the reason is dropped"]
    STR --> RES{"GatewayResult (:83)"}
    ADY --> RES
    RES -- "Succeeded" --> REC["payment.recordRefund(amount) (:84)<br/>refunded += amount, new status<br/>payments.save (:85)"]
    RES -- "anything else" --> E422b["throw PaymentRuleViolationException (:87)<br/>422 Refund failed at provider<br/>nothing recorded, key not stored"]
    REC --> SAVEKEY["IdempotencyService stores key → payment id"]
    SAVEKEY --> OUT["find(merchant, id) again (Controller:78)<br/>200 with the updated payment"]
    SKIP --> OUT

    classDef bad fill:#fde2e1,stroke:#c0392b,color:#000
    classDef err fill:#f3f3f3,stroke:#888,color:#000
    classDef ok fill:#e3f4e1,stroke:#2e7d32,color:#000
    class KH,DISP,ADY bad
    class E400a,E400b,E404,E422a,E422b err
    class REC,OUT ok
```

One request reads the payment **three times**: the controller's `find` to learn the currency, the
service's own load, and the final `find` for the response.

---

## 2. The happy path: a partial refund at Stripe

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (acme)
    participant API as PaymentController
    participant IS as IdempotencyService
    participant PS as PaymentService
    participant P as Payment (model)
    participant DB as Database
    participant ST as Stripe

    Note over C,ST: pay_1 is CAPTURED at stripe, 100.00 EUR, nothing refunded yet
    C->>API: POST /payments/pay_1/refunds 30.00, reason duplicate, key r1
    API->>DB: find(acme, pay_1) for the currency
    API->>IS: execute(acme, POST /payments/pay_1/refunds, r1)
    IS->>DB: findResult(r1)
    DB-->>IS: empty
    IS->>PS: refund(acme, pay_1, 30.00 EUR, duplicate)
    PS->>DB: findById(pay_1), check the merchant
    PS->>P: checkRefundable(30.00)
    P-->>PS: ok
    PS->>ST: POST /v1/refunds payment_intent pi_1, amount 3000, reason duplicate<br/>Idempotency-Key ref_7f…
    ST-->>PS: 200 id re_1, status succeeded
    PS->>P: recordRefund(30.00)
    Note over P: refunded 30.00, status PARTIALLY_REFUNDED
    PS->>DB: save pay_1
    IS->>DB: save r1 → pay_1
    API->>DB: find(acme, pay_1)
    API-->>C: 200, status PARTIALLY_REFUNDED, refundedMinor 3000
```

A retry with key `r1` stops at `findResult(r1)` and returns the same payment without calling Stripe
again.

---

## 3. The refund rules (`Payment.checkRefundable`, lines 78-89)

```mermaid
flowchart TD
    IN["checkRefundable(requested)"] --> S{"status CAPTURED or<br/>PARTIALLY_REFUNDED? (:79)"}
    S -- "no" --> X1["422: only captured payments can be refunded<br/>(CREATED, DECLINED, FAILED, OUTCOME_UNKNOWN, REFUNDED)"]
    S -- "yes" --> CU{"same currency as the payment? (:82)"}
    CU -- "no" --> X2["IllegalArgumentException → 400<br/>can't happen via the API:<br/>the controller takes the payment's currency"]
    CU -- "yes" --> Z{"requested is zero? (:83)"}
    Z -- "yes" --> X3["422: a refund needs an amount above zero"]
    Z -- "no" --> AMT{"⚠ requested > ORIGINAL amount? (:86)<br/>earlier refunds are not subtracted"}
    AMT -- "yes" --> X4["422: exceeds the captured amount"]
    AMT -- "no" --> OK["allowed → call the provider"]

    classDef bad fill:#fde2e1,stroke:#c0392b,color:#000
    class AMT bad
```

`recordRefund` (lines 92-96) runs the same check again, adds the amount, then sets `REFUNDED` when
the total **equals** the payment amount, otherwise `PARTIALLY_REFUNDED`.

### 3a. Two refunds that add up to more than the payment

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (acme)
    participant PS as PaymentService
    participant P as Payment
    participant ST as Stripe

    Note over C,ST: pay_1 CAPTURED, 100.00
    C->>PS: refund 60.00 (key r1)
    PS->>P: checkRefundable(60.00): 60 ≤ 100, ok
    PS->>ST: refund 60.00
    ST-->>PS: succeeded
    Note over P: refunded 60.00, PARTIALLY_REFUNDED
    C->>PS: refund 60.00 (key r2)
    PS->>P: checkRefundable(60.00): 60 ≤ 100, ok ⚠ only 40.00 is left
    PS->>ST: refund 60.00
    ST-->>PS: 400 charge_already_refunded → Failed NOTHING_CHARGED
    PS-->>C: 422 "Refund failed at stripe: …"
    Note over C,ST: No money moved, but only because Stripe refused. We asked for money we should<br/>never have asked for, and the client gets a provider error instead of "only 40.00 left".
```

Adyen refuses the same way (`422`, code `167 Refund amount exceeded`). If a provider didn't check,
our record would show 120.00 refunded on a 100.00 payment, still marked `PARTIALLY_REFUNDED`,
because the status test is "equals".

---

## 4. Choosing the provider and reading its answer

The refund always goes to `payment.providerId()`, never through routing or fallback: only the
provider that charged can refund.

```mermaid
flowchart LR
    PS["PaymentService.refund"] --> G["gateways.get(payment.providerId())"]
    G --> D{"⚠ instanceof StripeGateway<br/>service knows a concrete provider"}
    D -- "Stripe" --> SR["StripeGateway.refund(request, reason)<br/>Stripe-only method, outside the PaymentGateway contract"]
    D -- "any other" --> AR["PaymentGateway.refund(request)<br/>RefundRequest has no reason field"]
    SR --> SM["StripeMapper.toRefundResult<br/>200 → Succeeded(re_…)<br/>else ProviderHttp.failure"]
    AR --> AM["AdyenMapper.toRefundResult<br/>any 2xx → Succeeded(pspReference)<br/>else ProviderHttp.failure"]

    classDef bad fill:#fde2e1,stroke:#c0392b,color:#000
    class D,SR bad
```

| | Stripe-like | Adyen-like |
|---|---|---|
| Request | `POST /v1/refunds` | `POST /v71/payments/{psp}/refunds` |
| Our idempotency key to the provider | `Idempotency-Key: ref_…` (new UUID per call) | `Idempotency-Key: ref_…`, also sent as `reference` |
| Body | `payment_intent`, `amount`, `reason` (left out when null) | `amount`, `merchantAccount`, `reference`. **No reason** |
| Success reply | `200 {id: re_…, status: succeeded}` | `201 {pspReference, status: received}` |
| How we read it | `Succeeded`. The `status` field is parsed but never checked | `Succeeded` for any 2xx. `received` means *accepted*, not *done*; Level 3 has no webhook to learn the final outcome |
| Over-refund reply | `400 charge_already_refunded` → `NOTHING_CHARGED` | `422`, code `167` → `NOTHING_CHARGED` |
| Lost response | read timeout or connection cut → `OUTCOME_UNKNOWN` | same |

Every non-success becomes **HTTP 422 "rule violation"**, even a provider outage (503) or an
unknown outcome. See section 3 of `LOST-RESPONSE-AND-IDEMPOTENCY-FLOWS.md` for what a lost refund
response does when the client retries.

---

## 5. Payment status during refunds

```mermaid
stateDiagram-v2
    direction LR
    CAPTURED --> PARTIALLY_REFUNDED: refund, total below the amount
    CAPTURED --> REFUNDED: refund, total equals the amount
    PARTIALLY_REFUNDED --> PARTIALLY_REFUNDED: another partial refund
    PARTIALLY_REFUNDED --> REFUNDED: total reaches the amount
    REFUNDED --> REFUNDED: any refund → 422, not refundable
    note right of PARTIALLY_REFUNDED
        Also where a total ABOVE the amount
        would land, since the check is
        "equals", not "at least".
    end note
```

`CREATED`, `DECLINED`, `FAILED` and `OUTCOME_UNKNOWN` payments can't be refunded (422).

---

## 6. Summary: every outcome

| Situation | Client gets | Payment after | Money at the provider |
|---|---|---|---|
| `amountMinor` missing or ≤ 0 | 400 | unchanged | nothing |
| Unknown id, or another merchant's payment | 404 | unchanged | nothing |
| No `Idempotency-Key` (checked after the 404) | 400 | unchanged | nothing |
| Payment not `CAPTURED` / `PARTIALLY_REFUNDED` | 422 | unchanged | no call made |
| Amount above the original payment | 422 | unchanged | no call made |
| ⚠ Amount within the original but above what's left (3a) | 422 "Refund failed at …" | unchanged | provider asked, refused |
| Provider down (connection refused) or 503 | 422 "Refund failed at …" | unchanged | nothing |
| Provider succeeds | 200 | refunded += amount, `PARTIALLY_REFUNDED` / `REFUNDED` | refunded |
| Same key retried after a success | 200, same payment | unchanged | nothing new |
| ⚠ Lost response, then a retry with the same key | 422, then 200 | records **one** refund | refunded **twice** |
| ⚠ Adyen payment with a `reason` | 200 | refunded | refunded, reason lost |
| ⚠ Two refunds at the same moment | 200 twice | both read the same state, last save wins, so one refund is missing from our total | both refunded |
| ⚠ A key used before, by anyone, on any operation | 200 | unchanged | **nothing refunded** |
