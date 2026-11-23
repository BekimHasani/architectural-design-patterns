# Level 3 as coded: lost provider responses and idempotency

> **⚠ Spoiler.** This page draws what the Level 3 code *actually does* on these paths, including
> some of the planted issues. Read it only after writing your `NOTES.md`, or if you've decided to
> look.

These diagrams follow the code as it is today, not as it should be. `⚠` marks a step where the
code breaks one of the repo's domain invariants (see the root `CLAUDE.md`). Line numbers refer to
`src/main/java/com/designpatterns/level03/`.

Two ideas this page is about:

- **Outcome unknown.** When a request reached the provider but no usable answer came back (read
  timeout, connection cut, a 500, an unreadable body), the provider *may have charged*. The only
  safe moves are to record "unknown" and find out later (reconciliation, by our attempt id).
  Charging somewhere else can take the money twice.
- **Idempotency key.** The client sends an `Idempotency-Key` so it can retry safely: the same
  request with the same key must run at most once and return the same result. That only works if
  the key is scoped (who sent it, to which operation) and claimed *before* the work starts.

---

## 1. How a provider reply becomes a `GatewayResult`

`integration/support/ProviderHttp.java` plus the two mappers. Adapters never throw: every outcome
becomes one of three results. This part is correct; the trouble starts in who reads it.

```mermaid
flowchart TD
    A["Adapter sends the HTTP request<br/>connect timeout 1 s, read timeout 2 s<br/>(ProviderHttp:36-37)"] --> B{"Transport exception?"}
    B -- "ConnectException or<br/>HttpConnectTimeoutException" --> NC1["Failed NOTHING_CHARGED<br/>the request never left (ProviderHttp:58)"]
    B -- "read timeout, connection reset<br/>(sent, no answer)" --> OU1["Failed OUTCOME_UNKNOWN<br/>(ProviderHttp:59)"]
    B -- "no, a reply arrived" --> C{"HTTP status"}
    C -- "Stripe 402 with card_error" --> DE1["Declined"]
    C -- "other 4xx, or 503" --> NC2["Failed NOTHING_CHARGED<br/>rejected before processing (ProviderHttp:66)"]
    C -- "any other 5xx" --> OU2["Failed OUTCOME_UNKNOWN"]
    C -- "200" --> D{"Body readable?"}
    D -- "no" --> OU3["Failed OUTCOME_UNKNOWN<br/>(ProviderHttp:72)"]
    D -- "yes" --> E{"Provider outcome"}
    E -- "Stripe succeeded / Adyen Authorised" --> S["Succeeded(providerReference)"]
    E -- "Adyen Refused" --> DE2["Declined"]
    E -- "anything unexpected" --> OU4["Failed OUTCOME_UNKNOWN"]

    classDef unknown fill:#fde2e1,stroke:#c0392b,color:#000
    classDef safe fill:#e3f4e1,stroke:#2e7d32,color:#000
    classDef result fill:#e1ecfd,stroke:#2b5fb4,color:#000
    class OU1,OU2,OU3,OU4 unknown
    class NC1,NC2 safe
    class S,DE1,DE2 result
```

Green means it's safe to try another provider; red means the money may already have moved.

---

## 2. Charging: the fallback loop

`service/payment/PaymentService.charge` (lines 39-62).

```mermaid
flowchart TD
    R["POST /payments<br/>passes the idempotency check (section 4)"] --> P0["Payment.create → CREATED<br/>payments.save BEFORE any provider call (:40-41)"]
    P0 --> L{"Next provider in the route? (:44)<br/>EUR: adyen, stripe<br/>others: stripe, adyen"}
    L -- "yes" --> AT["New attemptId att_… (:45)<br/>gateway.charge(attemptId, amount)<br/>attemptId is only logged, never stored"]
    AT --> RES{"GatewayResult (:49-58)"}
    RES -- "Succeeded" --> CAP["markCaptured(provider, reference)<br/>save → CAPTURED"]
    RES -- "Declined" --> DEC["markDeclined → DECLINED<br/>no fallback (correct)"]
    RES -- "Failed, ANY kind" --> NEXT["⚠ log and try the next provider<br/>failed.kind() is never read<br/>OUTCOME_UNKNOWN falls back too"]
    NEXT --> L
    L -- "no providers left" --> F["⚠ markFailed(lastFailure) → FAILED (:60)<br/>even when a provider may have charged<br/>markOutcomeUnknown is never called"]
    CAP --> STORE["IdempotencyService stores key → payment id"]
    DEC --> STORE
    F --> STORE
    STORE --> RESP["201 Created + payment JSON<br/>(also for DECLINED and FAILED)"]

    classDef bad fill:#fde2e1,stroke:#c0392b,color:#000
    class NEXT,F bad
```

### 2a. Lost response at the first provider, second one succeeds: charged twice

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (acme)
    participant API as PaymentController
    participant IS as IdempotencyService
    participant PS as PaymentService
    participant DB as Database
    participant ST as Stripe
    participant AD as Adyen

    C->>API: POST /payments USD 19.99, Idempotency-Key k1
    API->>IS: execute(acme, POST /payments, k1)
    IS->>DB: findResult(k1)
    DB-->>IS: empty
    IS->>PS: charge(...)
    PS->>DB: save pay_1 CREATED
    PS->>ST: charge att_1
    Note over ST: Stripe charges 19.99,<br/>then the connection drops
    ST--xPS: no answer within 2 s
    Note over PS: Failed OUTCOME_UNKNOWN,<br/>handled like any failure
    PS->>AD: charge att_2 (fallback)
    Note over AD: Adyen charges 19.99
    AD-->>PS: Authorised psp_9
    PS->>DB: save pay_1 CAPTURED at adyen
    IS->>DB: save k1 → pay_1
    API-->>C: 201 CAPTURED, providerId adyen
    Note over C,AD: The customer paid 39.98. Our record shows one Adyen charge.<br/>Nothing stores att_1, so reconciliation can't even find the Stripe charge.
```

### 2b. Lost response, then a safe failure: the record says FAILED, but the money moved

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (acme)
    participant PS as PaymentService
    participant ST as Stripe
    participant AD as Adyen

    C->>PS: POST /payments USD 19.99, key k1
    PS->>ST: charge att_1
    Note over ST: Stripe charges 19.99
    ST--xPS: read timeout → OUTCOME_UNKNOWN
    PS->>AD: charge att_2
    AD-->>PS: 503 → NOTHING_CHARGED
    Note over PS: no providers left → markFailed
    PS-->>C: 201, status FAILED
    Note over C: FAILED reads as "nothing charged",<br/>so the customer pays again
    C->>PS: POST /payments USD 19.99, NEW key k2
    PS->>ST: charge att_3
    ST-->>PS: succeeded
    PS-->>C: 201 CAPTURED
    Note over C,ST: Stripe charged the customer twice. We record one FAILED and one CAPTURED payment.
```

The same happens when *every* provider answers with an unknown outcome: the payment ends `FAILED`,
and any or all of them may have charged.

---

## 3. Refunding: a lost response makes the client retry

`service/payment/PaymentService.refund` (lines 65-85) and `PaymentController.refund` (lines 65-79).

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (acme)
    participant IS as IdempotencyService
    participant PS as PaymentService
    participant ST as Stripe

    Note over C,ST: pay_1 is CAPTURED, 100.00 at Stripe
    C->>IS: POST /payments/pay_1/refunds 40.00, key k3
    IS->>IS: findResult(k3) → empty
    IS->>PS: refund(acme, pay_1, 40.00)
    PS->>PS: checkRefundable(40.00) ok
    PS->>ST: refund, Idempotency-Key ref_1 (new UUID, :71)
    Note over ST: Stripe refunds 40.00
    ST--xPS: read timeout → OUTCOME_UNKNOWN
    PS-->>IS: throws PaymentRuleViolationException (:84)
    Note over IS: exception before keys.save,<br/>so k3 stays free
    IS-->>C: 422 "Refund failed at stripe"
    C->>IS: retry, same key k3
    IS->>IS: findResult(k3) → empty, runs again
    IS->>PS: refund(acme, pay_1, 40.00)
    PS->>ST: refund, Idempotency-Key ref_2 (another new UUID)
    Note over ST: Stripe refunds another 40.00,<br/>since ref_2 ≠ ref_1 it can't dedupe
    ST-->>PS: succeeded
    PS->>PS: recordRefund(40.00), save
    IS->>IS: save k3 → pay_1
    IS-->>C: 200, refundedMinor 4000
    Note over C,ST: The merchant gave back 80.00. Our record says 40.00.
```

An unknown refund outcome is reported as a business-rule violation (HTTP 422), and the attempt
isn't recorded anywhere, so nothing can follow it up.

---

## 4. Idempotency: what `execute` does

`service/idempotency/IdempotencyService.execute` (lines 29-40) and the controller around it.

```mermaid
flowchart TD
    A["POST with X-Merchant-Id and Idempotency-Key"] --> B{"Key blank?"}
    B -- "yes" --> E400["400 Idempotency-Key header is required"]
    B -- "no" --> C["⚠ findResult(idempotencyKey) (:33)<br/>looked up by the key ALONE:<br/>merchantId and operation are ignored"]
    C --> D{"Found?"}
    D -- "yes" --> REPLAY["Return the stored id<br/>the command does NOT run"]
    D -- "no" --> RUN["Run the command (:37)<br/>charge or refund"]
    RUN -- "returns an id" --> SAVE["⚠ save(key → id) (:38)<br/>only AFTER the work finished"]
    RUN -- "throws" --> NOSAVE["Nothing stored, the key stays free<br/>(400 / 404 / 422 to the client)"]
    SAVE --> OUT["Return the id"]
    REPLAY --> OUT
    OUT --> FIND["Controller: find(merchantId, id)"]
    FIND -- "the id belongs to the caller" --> OK["201 or 200 with the payment"]
    FIND -- "the id belongs to another merchant" --> NF["404 Payment not found"]

    classDef bad fill:#fde2e1,stroke:#c0392b,color:#000
    class C,SAVE bad
```

### 4a. The life of an idempotency key

```mermaid
stateDiagram-v2
    [*] --> Free
    Free --> InFlight: first request, findResult misses
    InFlight --> Stored: command returned an id
    InFlight --> Free: command threw (400, 404, 422, 500)
    Stored --> Stored: every later request with this key, from ANY merchant, on ANY operation
    note right of InFlight
        Not written anywhere. A second request
        arriving now also sees Free and
        runs the command again.
    end note
    note right of Stored
        Kept forever. No expiry, and no hash of
        the request, so the same key with a
        different amount returns the first payment.
    end note
```

### 4b. The client retries while the first request is still running

A provider read timeout alone takes 2 s per provider, so a client with a short timeout retries
while the first request is still in the fallback loop.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (acme)
    participant IS as IdempotencyService
    participant DB as Database
    participant PS as PaymentService

    C->>IS: POST /payments, key k1 (request A)
    IS->>DB: findResult(k1) → empty
    IS->>PS: charge → pay_1 (slow, still running)
    Note over C: client times out and retries
    C->>IS: POST /payments, key k1 (request B)
    IS->>DB: findResult(k1) → empty, nothing claimed it
    IS->>PS: charge → pay_2
    PS-->>IS: pay_1 CAPTURED (request A)
    IS->>DB: save k1 → pay_1
    PS-->>IS: pay_2 CAPTURED (request B)
    IS->>DB: save k1 → pay_2
    Note over DB: Spring Data save() on an existing id merges,<br/>so pay_2 silently overwrites pay_1
    Note over C,PS: Two charges for one key. The key now points at pay_2, so pay_1 is unreachable by key.
```

### 4c. The same key from another merchant, or on another operation

```mermaid
sequenceDiagram
    autonumber
    participant A as Acme
    participant G as Globex
    participant IS as IdempotencyService
    participant API as PaymentController

    A->>IS: POST /payments, key order-42
    IS-->>A: runs, stores order-42 → pay_A, 201
    G->>IS: POST /payments, key order-42
    IS-->>API: found → pay_A, Globex's charge never runs
    API->>API: find(globex, pay_A) → not Globex's
    API-->>G: 404 Payment not found
    Note over G: Globex isn't charged, and every retry<br/>with this key returns 404 again

    A->>IS: POST /payments/pay_A/refunds 10.00, key order-42
    IS-->>API: found → pay_A, the refund never runs
    API-->>A: 200 with pay_A unchanged
    Note over A: looks like success, but nothing was refunded
```

---

## 5. Summary: what each party sees

| Scenario | The client gets | We store | What really moved | Invariant broken |
|---|---|---|---|---|
| Lost response at provider 1, provider 2 succeeds (2a) | 201 `CAPTURED` | `CAPTURED` at provider 2 | charged **twice** | never fall back after an unknown outcome |
| Lost response, provider 2 fails safely (2b) | 201 `FAILED` | `FAILED` | charged once, unrecorded | unknown must stay `OUTCOME_UNKNOWN` until reconciled |
| Lost response at every provider | 201 `FAILED` | `FAILED` | possibly charged at each | same |
| Connection refused / 503 at provider 1, provider 2 succeeds | 201 `CAPTURED` | `CAPTURED` | charged once | none, this fallback is safe |
| Declined | 201 `DECLINED` | `DECLINED` | nothing | none, no fallback (correct) |
| Same key retried after the first request finished | 201, same payment | unchanged | nothing new | none, this is idempotency working |
| Same key retried while the first is still running (4b) | 201 twice | two payments, key → the last | charged **twice** | the key must be claimed before the work |
| Refund with a lost response, retried with the same key (3) | 422, then 200 | refunded 40.00 | refunded **80.00** | the key was never stored, so the retry runs again with a new provider key |
| Another merchant reuses a key (4c) | 404 | nothing | Globex never charged | the key must be scoped to the merchant |
| A charge key reused for a refund (4c) | 200, unchanged | nothing | nothing refunded | the key must be scoped to the operation |

For the intended design, compare Level 2: `service/payment/GatewayResultApplier` (how a result,
including an unknown one, changes a payment) and `presentation/rest/IdempotentRequestExecutor` +
`service/idempotency/IdempotencyService` (keys scoped to merchant + path).
