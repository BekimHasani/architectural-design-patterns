# Architecture? (original interview script)

> Archived original draft. Kept for reference only; the source of truth is
> [Architecture.md](../Architecture.md). Formatting was repaired (diagrams and
> folder trees were flattened by a PDF/doc export); the wording is unchanged.

A much better approach is to give the candidate a realistic scenario and ask them to **design the source-code architecture**, then progressively introduce complications that force architectural decisions.

Below is a progression from **easy → very hard**.

## Question 1 — Basic: Multiple providers

### Scenario

You are building an **Order Checkout Service**.

When a customer checks out, the system needs to calculate the final order total and then charge the customer.

The company wants to support multiple payment providers:

- Stripe
- PayPal
- Adyen

Initially, only Stripe is implemented, but PayPal and Adyen will be added later.

The application should be able to switch providers based on configuration.

### Interview question

**How would you structure the source code for this application?**

In particular:

1. Where would you put the business logic?
2. Where would you put the Stripe/PayPal/Adyen implementations?
3. How would you prevent the business logic from becoming dependent on Stripe?
4. What interfaces would you create?
5. Show me approximately what the project/folder structure would look like.

### What you're looking for

A good candidate might arrive at something like:

```
src/
├── domain/
│   └── payment/
│       ├── Payment.ts
│       └── PaymentProvider.ts
│
├── application/
│   └── checkout/
│       └── CheckoutService.ts
│
├── infrastructure/
│   └── payments/
│       ├── StripePaymentProvider.ts
│       ├── PayPalPaymentProvider.ts
│       └── AdyenPaymentProvider.ts
│
└── api/
    └── CheckoutController.ts
```

The key architectural idea is:

```
                 ┌─────────────────────┐
                 │   CheckoutService   │
                 └──────────┬──────────┘
                            │
                            ▼
                 ┌─────────────────────┐
                 │  PaymentProvider    │
                 │     interface       │
                 └──────────┬──────────┘
                            │
                ┌───────────┼───────────┐
                ▼           ▼           ▼
             Stripe       PayPal      Adyen
```

The candidate doesn't necessarily need to call this **Hexagonal Architecture**, **Clean Architecture**, or **Ports & Adapters**.

You are looking for whether they naturally arrive at **dependency inversion and an abstraction around external systems**.

## Question 2 — Intermediate: Different APIs, different capabilities

Now make the problem more interesting.

### Scenario

The same checkout system now supports three payment providers.

But the providers have different APIs:

**Stripe**

```
POST /payment_intents
```

**PayPal**

```
POST /orders
POST /orders/{id}/capture
```

**Adyen**

```
POST /payments
```

Your business application should expose one consistent operation:

```ts
paymentService.charge(...)
```

The business logic should not know which provider is being used.

Additionally, the providers return completely different response formats.

### Interview question

Design the architecture and source-code structure.

- How would you translate the different provider APIs into a common application/domain model?
- Where would the translation happen?
- Would you create a common `PaymentResponse`?
- How would you handle provider-specific errors?

### Follow-up

Ask:

> Suppose PayPal requires a two-step process while Stripe requires one API call. Should the application know that PayPal requires two calls?

This is a **very revealing question**.

A strong candidate will usually argue that the application should not need to know provider-specific workflow details.

For example:

```
Application
    │
    ▼
PaymentProvider
    │
    ├── StripeAdapter
    │
    ├── PayPalAdapter
    │
    └── AdyenAdapter
```

The adapter absorbs the differences.

## Question 3 — Intermediate/Advanced: Provider selection

Now introduce selection logic.

### Scenario

The company wants to choose the payment provider dynamically.

Rules:

- EUR → Adyen
- USD → Stripe
- GBP → PayPal
- If Stripe is unavailable → use Adyen
- Some enterprise customers have a specific provider configured
- The provider can also be changed through configuration

### Interview question

- Where would you put the provider-selection logic?
- Would the controller decide?
- Would `CheckoutService` decide?
- Would you introduce a factory?
- Would you introduce a strategy?
- How would you keep this logic from becoming a large if/else statement?

Then ask:

> Draw the dependency diagram.

This separates candidates who understand **interfaces** from candidates who understand **architecture**.

## Question 4 — Advanced: Provider failure and fallback

Now make it production-like.

### Scenario

The payment system has:

- Stripe
- PayPal
- Adyen

The preferred provider is Stripe.

However:

- Stripe can timeout.
- Stripe can return HTTP 500.
- Stripe can return "card declined."
- Stripe can accept the request but your application may not receive the response because of a network failure.

The business wants to automatically use another provider when appropriate.

### Interview question

How would you design the architecture so that payment-provider failures can be handled without putting Stripe-specific logic into the checkout business logic?

Then ask:

> Which errors should trigger fallback and which should not?

For example:

```
Card declined
    ↓
Do NOT retry with another provider
```

versus:

```
Stripe timeout
    ↓
Potentially retry/fallback
```

Then introduce the really important question:

> What happens if Stripe actually charged the customer, but our application timed out before receiving the response?

Now you're testing whether they understand **distributed systems**, not merely architecture patterns.

A strong candidate should start talking about:

- idempotency
- payment transaction IDs
- idempotency keys
- payment states
- reconciliation
- retries
- eventual consistency

## Question 5 — Advanced: Webhooks

Add another requirement.

### Scenario

Payment providers don't always return the final payment state immediately.

For example:

```
Application
    ↓
Stripe
    ↓
"Payment processing"
```

Later:

```
Stripe
    ↓
Webhook
    ↓
Your application
    ↓
Payment = COMPLETED
```

PayPal and Adyen have similar mechanisms, but their webhook payloads are completely different.

### Interview question

How would you architect webhook processing so that Stripe, PayPal, and Adyen can all update the same internal payment model?

Then ask:

> Where does the provider-specific webhook DTO belong?

And:

> Should your domain know what a Stripe webhook is?

The ideal direction is:

```
Stripe Webhook
    ↓
StripeWebhookController
    ↓
StripeWebhookMapper
    ↓
Application Command
    ↓
Domain
```

rather than:

```
Domain
    ↓
if Stripe...
if PayPal...
if Adyen...
```

## Question 6 — Hard: Design the entire architecture

This is the one I would use for a **senior software engineer / architect interview**.

### Scenario

You are designing a **multi-provider payment platform**.

The platform must support:

#### Payment providers

- Stripe
- PayPal
- Adyen
- Future providers not yet known

#### Operations

The system must support:

- Authorize payment
- Capture payment
- Cancel authorization
- Refund
- Partial refund
- Check payment status

#### Provider differences

Each provider:

- Has a different API
- Has different authentication
- Has different request/response models
- Has different error codes
- Has different payment workflows
- Has different webhook formats
- May have different capabilities

For example:

```
Stripe:      authorize → capture
PayPal:      create order → approve → capture
Provider X:  charge directly
```

#### Business requirements

The company wants:

- Provider selection based on currency
- Provider selection based on merchant
- Automatic fallback where safe
- Retry of transient failures
- Idempotent payment operations
- Webhook processing
- Audit history
- Provider-independent business logic
- Ability to add a new provider without modifying core business logic

### Interview question

**Design the architecture of this system.**

You don't need to implement the APIs. I want you to design the source-code architecture and explain the boundaries between components.

You can use any architectural approach you believe is appropriate: layered architecture, Clean Architecture, Hexagonal Architecture, Ports & Adapters, or another approach.

Please show:

1. The project/folder structure.
2. The major interfaces.
3. The dependency direction.
4. Where business rules live.
5. Where provider-specific code lives.
6. How provider selection works.
7. How provider-specific errors are translated.
8. How webhooks enter the system.
9. How retries and idempotency are handled.
10. How you would add a fourth provider without modifying the core payment logic.

## Question 7 — Expert: Challenge their architecture

After they present their solution, don't stop.

Give them this requirement:

Six months later, the company adds **20 payment providers**.

Each provider has slightly different capabilities.

- Some support refunds.
- Some don't.
- Some support authorization/capture.
- Some only support immediate payment.
- Some support recurring payments.
- Some have asynchronous confirmation.
- Some don't support certain currencies.

Then ask:

> **Does your original abstraction still work?**

This is a fantastic architectural question because the candidate now has to confront **leaky abstractions**.

For example, a simplistic interface:

```ts
interface PaymentProvider {
  charge();
  refund();
}
```

might look nice initially but become problematic when providers have fundamentally different capabilities.

A stronger design might introduce capabilities:

```
PaymentProvider
PaymentAuthorizationProvider
RefundProvider
RecurringPaymentProvider
WebhookProvider
```

or another capability-oriented design.

Then ask:

> Would you prefer one large interface or multiple smaller interfaces? Why?

Now you're testing **interface segregation and domain modeling**, rather than whether they know the name "Hexagonal Architecture."

## The progression I would use in an actual interview

| Level | What you're testing |
|-------|---------------------|
| **1. Multiple APIs** | Abstraction, dependency inversion |
| **2. Different API models** | Adapters, mapping, boundaries |
| **3. Provider selection** | Strategy/factory, separation of concerns |
| **4. Failures & fallback** | Distributed systems thinking |
| **5. Webhooks** | Async architecture, boundaries |
| **6. Complete system** | Architecture and domain modeling |
| **7. 20 providers** | Scalability of abstractions, SOLID, extensibility |

## The most important thing

I would **not score the candidate based on whether they say "Hexagonal Architecture."**

Someone could say:

> "I'd use Clean Architecture."

and then produce terrible boundaries.

Another candidate might say:

> "I'd create an application service, a PaymentGateway interface, and adapters for Stripe and PayPal. The application depends on the interface, while infrastructure implements it."

That's the candidate I'd be interested in—even if they never use the words *Clean Architecture* or *Hexagonal Architecture*.

The real interview question is:

**Can the candidate identify what belongs to the business, what belongs to the outside world, and how to prevent changes in the outside world from contaminating the business?**

That's the architectural skill you're actually trying to measure.
