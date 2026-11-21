# Provider Simulators

Local fake payment providers, used by both levels as a test-scoped dependency. They exist so
that:

- the demos are **free and deterministic** (no real API keys),
- each provider has a **genuinely different API**, so adapters have real work to do,
- failures can be **injected on demand** (timeouts, 5xx, lost responses, webhook faults).

> The simulators are test tooling, **not** part of the architecture being studied.
> Keep them simple; don't apply the levels' patterns here.

The shapes are *inspired by* Stripe, PayPal and Adyen, but simplified. Don't use
them as documentation of the real APIs.

---

## 1. API shapes

### "Stripe-like" sim (port 4101)

| | |
|---|---|
| Auth | `Authorization: Bearer sk_test_...` |
| Idempotency | `Idempotency-Key` header |
| Create & confirm | `POST /v1/payment_intents` `{ amount: 1999, currency: "eur", confirm: true, capture_method: "automatic" \| "manual" }` |
| Capture | `POST /v1/payment_intents/{id}/capture` |
| Refund | `POST /v1/refunds` `{ payment_intent, amount? }` |
| Status | `GET /v1/payment_intents/{id}` |
| Statuses | `requires_action`, `processing`, `requires_capture`, `succeeded`, `canceled` |
| Error body | `{ error: { type: "card_error", code: "card_declined", decline_code: "insufficient_funds" } }` |
| Webhook | `POST` to your URL, header `Sim-Signature: t=...,v1=<hmac-sha256>`, body `{ id, type: "payment_intent.succeeded", data: { object: {...} } }` |

### "PayPal-like" sim (port 4102)

| | |
|---|---|
| Auth | OAuth2 client-credentials: `POST /v1/oauth2/token` → bearer token (expires) |
| Idempotency | `PayPal-Request-Id` header |
| Create order | `POST /v2/checkout/orders` `{ intent: "CAPTURE", purchase_units: [{ amount: { currency_code: "EUR", value: "19.99" } }] }` → `status: "CREATED"`, `links: [{ rel: "approve", href }]` |
| Buyer approval | Simulated: `POST /sim/orders/{id}/approve` (stands in for the customer clicking "Approve" at PayPal) |
| Capture | `POST /v2/checkout/orders/{id}/capture` |
| Statuses | `CREATED`, `APPROVED`, `COMPLETED`, `VOIDED` |
| Error body | `{ name: "UNPROCESSABLE_ENTITY", details: [{ issue: "INSTRUMENT_DECLINED" }] }` |
| Webhook | headers `Sim-Transmission-Id`, `Sim-Transmission-Sig`; body `{ id, event_type: "PAYMENT.CAPTURE.COMPLETED", resource: {...} }` |

Note: amounts are **decimal strings** here, unlike the Stripe-like sim's integer
minor units. Your adapter has to convert.

### "Adyen-like" sim (port 4103)

| | |
|---|---|
| Auth | `X-API-Key` header + `merchantAccount` in body |
| Idempotency | `Idempotency-Key` header |
| Pay | `POST /v71/payments` `{ amount: { value: 1999, currency: "EUR" }, reference, merchantAccount, paymentMethod }` |
| Capture | `POST /v71/payments/{pspReference}/captures` |
| Refund | `POST /v71/payments/{pspReference}/refunds` |
| Result | `resultCode`: `Authorised`, `Refused`, `Pending`, `RedirectShopper`, `Error` (HTTP 200 even when refused!) |
| Error body | `{ status: 422, errorCode: "14_004", message: "..." }` |
| Webhook | **Batched**: `{ notificationItems: [{ NotificationRequestItem: { eventCode: "AUTHORISATION", success: "true", pspReference, additionalData: { hmacSignature } } }] }`; must reply with body `[accepted]` |

---

## 2. Fault injection

Each sim accepts a control header on any request, or a control endpoint to set a
sticky fault mode:

```
X-Sim-Fault: <mode>
POST /sim/faults  { mode: "<mode>", count: 3 }   # next 3 requests
```

| Mode | Behaviour | Exercises |
|------|-----------|---------|
| `none` | Normal | all |
| `decline` | Hard decline (card declined / refused) | error mapping, fallback |
| `soft-decline` | Retryable decline (e.g. insufficient funds, try later) | retry |
| `slow:<ms>` | Respond after delay | timeouts |
| `timeout` | Never respond | unknown outcome, reconciliation |
| `http-500` | Error **before** processing (no charge happens) | retry, safe fallback |
| `http-503` | Unavailable (no charge happens) | circuit breaker, safe fallback |
| `http-429` | Rate limited, with `Retry-After` | retry |
| `refuse-connection` | Socket closed before request accepted | safe fallback |
| `drop-after-commit` | **Charge succeeds**, then connection is cut before the response | idempotency, reconciliation |
| `requires-action` | Payment needs customer action (3DS / approval) | `RequiresAction` result |
| `async` | Returns pending; final result arrives later by webhook | webhooks |
| `webhook-duplicate` | Sends the same webhook twice | inbox dedup |
| `webhook-out-of-order` | Sends "succeeded" before "processing" | out-of-order events |
| `webhook-bad-signature` | Sends a webhook with an invalid signature | signature verification |

Sims keep in-memory state, honour idempotency keys like the real thing (same key →
same stored response), and expose `GET /sim/ledger` so tests can check **how many
times the customer was really charged**.

---

## 3. Layout

Plain JDK `HttpServer`, no Spring: it's test tooling and starts in-process in
milliseconds.

```
tools/provider-simulators/
├── pom.xml
└── src/main/java/com/designpatterns/simulators/
    ├── BaseSimulator.java         ✅ server, faults, ledger, /sim/* endpoints, completeCustomerAction()
    ├── StripeLikeSimulator.java   ✅ create / get / confirm / search payment intents, idempotency
    ├── PayPalLikeSimulator.java   ✅ OAuth token, create / get / capture / lookup orders, buyer approval, idempotency
    ├── AdyenLikeSimulator.java    ✅ payments, payments/details, lookup, HTTP 200 refusals, idempotency
    ├── Fault.java                 ✅ NONE, DECLINE, REQUIRES_ACTION, HTTP_500, HTTP_503, HTTP_429, TIMEOUT, DROP_AFTER_COMMIT
    ├── FaultInjector.java         ✅ sticky fault with a use count
    ├── Ledger.java                ✅ what was really charged (always minor units)
    ├── WebhookSender.java         ✅ signed webhooks + delivery faults (DUPLICATE, OUT_OF_ORDER, BAD_SIGNATURE, LOST)
    ├── SimHttp.java               ✅ JSON + path helpers
    └── SimulatorsMain.java        ✅ runs all sims on their default ports
```

Faults implemented so far: `none`, `decline`, `requires-action`, `http-500`,
`http-503`, `http-429` (sends `Retry-After: 1`), `timeout` and `drop-after-commit`.
The others in §2 are not implemented yet; add one when a test needs it.

> `timeout` and `drop-after-commit` both mean **the provider processes the request
> (charges the card), and the answer never arrives**. `timeout` holds it back for 10 s;
> `drop-after-commit` closes the connection without any response. That is the
> dangerous kind of failure, the reason the platform never falls back after one, and
> why reconciliation exists. "Connection refused" is tested by closing the simulator.

**Idempotency (all three sims).** A repeated create with the same key (`Idempotency-Key`
for Stripe/Adyen, `PayPal-Request-Id` for PayPal) returns the **stored** answer,
delivered normally, and never charges again.

**Lookup by our reference (reconciliation).** Stripe-like:
`GET /v1/payment_intents/search?query=metadata['attempt_id']:'att_1'` (shaped like
Stripe's search API). PayPal-like: `GET /v2/checkout/orders?reference_id=att_1`.
Adyen-like: `GET /v71/payments?merchantReference=att_1`. The last two are
simulator-only conveniences; real PayPal and Adyen lean on reports and webhooks.

The PayPal-like sim only accepts EUR, USD and GBP (`PayPalLikeSimulator.SUPPORTED_CURRENCIES`);
anything else gets `422 CURRENCY_NOT_SUPPORTED`. That makes the routing
conflict (merchant prefers PayPal, currency it can't take) real.

**Webhooks.** `sim.sendWebhooksTo(url, secret)` turns them on (`SimulatorsMain`
points them at `http://localhost:8080/webhooks/{stripe|paypal|adyen}`). An immediate result
is delivered **before** the API answer, so the "webhook beats the response" race is the
normal case. `ASYNC` (API fault) answers "processing" / "Received"; `stripe.settle(id, ok)`
and `adyen.settleAll()` (one batch for every pending payment) send the final event later.
Delivery faults are set with `sim.injectWebhookFault(WebhookSender.Mode.X)`:
`DUPLICATE`, `OUT_OF_ORDER`, `BAD_SIGNATURE`, `LOST`. `sim.webhookDeliveries()` shows what
the app answered (e.g. Adyen's required `[accepted]`).

| Sim | Signature |
|-----|-----------|
| Stripe-like | `Sim-Signature: t=<unix>,v1=<hex hmac_sha256(secret, t + "." + body)>` |
| PayPal-like | `Sim-Transmission-Sig = hex hmac_sha256(secret, transmissionId + "\|" + time + "\|" + body)` |
| Adyen-like | per item: `additionalData.hmacSignature = base64 hmac_sha256(key, psp:merchantRef:eventCode:success)` |

**Operations beyond a plain charge.** Manual capture (`capture_method: manual`, Adyen `manualCapture: true`), partial
capture, cancel/void, partial refund, with faults and idempotency on each. `sim.refunds()` is the
refund ledger, `sim.credentialsSeen()` proves which merchant's key was used. Failures that were
never processed (5xx, 429) are **not** stored under an idempotency key, so a retry really retries.
**Provider X** (port 4104, `ProviderXSimulator`) charges directly: no capture, no cancel.

**Synthetic providers (capability model).** `SyntheticSimulator` runs one small API in three modes: `HOLD` (port 4105, no
refunds), `CHARGE` (4106, charge only), `ASYNC` (4107, always pending until `settle(id)`).

Extra endpoints beyond §1: Stripe-like `POST /v1/payment_intents/{id}/confirm`
(after 3-D Secure) and Adyen-like `POST /v71/payments/details {pspReference}`
(after the shopper redirect).

Usage:

```java
// in a test: random free port, closed automatically
try (StripeLikeSimulator stripe = StripeLikeSimulator.start(0)) {
    var provider = new StripePaymentProvider(RestClient.builder(), stripe.baseUrl(), "sk_test_x");
    stripe.injectFault(Fault.REQUIRES_ACTION, 1);   // next payment needs 3-D Secure
    // ... provider.charge(...) → RequiresAction
    stripe.completeCustomerAction("pi_1");          // play the customer
    assertThat(stripe.ledger()).hasSize(1);         // what was REALLY charged
}
```

```bash
./mvnw -q install -DskipTests && ./mvnw -q exec:java -pl tools/provider-simulators   # ports 4101–4107
```

Keep it small: add only what a test needs.
