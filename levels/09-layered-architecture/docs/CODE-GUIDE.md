# Level 9 code guide: how to read and understand this application

This guide shows you **where to start**, **in which order to read**, and **how to analyse** the code
until the whole system makes sense. The code itself is commented along the way:

- every layer has a `package-info.java` that explains its job, its patterns and its annotations;
- every important step carries a `FLOW X.n` comment, so you can follow one request from start to end.

---

## 1. The big picture in one minute

```
presentation   HTTP controllers, webhook endpoint, timer          how the world reaches us
     │
  service      use cases (authorize, capture, refund, ...)         what the business does
     │
┌────┴──────┐
persistence  integration    database | payment providers           what we talk to
└────┬──────┘
   model       Payment, Money, ids, rules                          business words and rules

config         builds and wires everything (composition root)
```

**One rule:** a layer may only call the layers *below* it. `architecture/LayeredArchitectureTest`
breaks the build when an import goes the wrong way.

**The domain in three sentences:** a merchant asks us to take money from a customer. We pick a payment
provider (Stripe-like, PayPal-like, ...), ask it to *authorize* (hold) the money, and later *capture*,
*cancel* or *refund* it. Providers can be slow, down, or not answer at all, and the code is mostly about
never charging a customer twice and never losing track of money.

---

## 2. The flows (follow them with `grep`)

```bash
cd levels/09-layered-architecture/src/main/java
grep -rn "FLOW A" .     # authorize a payment       (start here)
grep -rn "FLOW B" .     # capture / cancel / refund
grep -rn "FLOW C" .     # provider webhooks
grep -rn "FLOW D" .     # reconciliation
grep -rn "FLOW S" .     # startup: how Spring wires it all
```

### FLOW A: `POST /payments` (authorize)

| Step | Where | What happens |
|---|---|---|
| A.1 | `presentation/rest/CorrelationIdFilter` | every request gets a correlation id for the logs |
| A.2 | `presentation/rest/PaymentController#authorize` | HTTP in: headers + raw body |
| A.3 | `IdempotentRequestExecutor` → `IdempotencyService` → `JpaIdempotencyKeyRepository` | claim the `Idempotency-Key`; a retry gets the stored answer |
| A.4 | `PaymentController` | JSON → DTO → `Money` |
| A.5 | `service/payment/PaymentService#authorize` | known merchant? |
| A.6 | `service/routing/ProviderRoutingService#candidates` | which providers could take it, in order |
| A.7 | `service/merchant/MerchantService#credentials` | ... and which has this merchant signed up with |
| A.8 | `Payment.create` + `JpaPaymentRepository#save` | create the aggregate and INSERT it |
| A.9 | `AuthorizationOrchestrator#authorize` | **write-ahead**: save "attempt X at provider Y" *before* calling |
| A.10 | `DefaultProviderGateway#call` → `CircuitBreakerPolicy` → `RetryPolicy` → `StripeGateway#authorize` → `StripeMapper` | the network call, outside any transaction |
| A.11 | `GatewayResultApplier#applyAuthorization` → `Payment#authorized/declined/...` | the provider's answer changes the payment (state machine) |
| A.12 | `AuthorizationOrchestrator#applyAndSave` | save; if a webhook was faster, reload and apply again |
| A.13 | `AuthorizationOrchestrator#authorize` | fall back to the next provider **only** if nothing was charged |
| A.14 | `PaymentService#finish` | automatic capture → continues as FLOW B |
| A.15 | `PaymentController#lifecycle` | payment status → HTTP status (200 / 202 / 402 / 502) |
| A.16 | `IdempotentRequestExecutor#runAndStore` | store the response for replays |
| errors | `ApiExceptionHandler` | exceptions → `ProblemDetail` JSON (400 / 403 / 404 / 409 / 422) |

### FLOW B: capture / cancel / refund
`PaymentController#capture` (B.1) → `PaymentOperationService#capture` (B.2 load and check) →
`Payment#startCapture` (B.3 rules and reservation) → `run`: **save first** (B.4, a concurrent request loses
here and nothing is sent) → gateway call with the operation id as the provider's idempotency key (B.5)
→ `record`: reload, apply, save, retry on conflict (B.6) → `operationOutcome` (B.7).

### FLOW C: `POST /webhooks/{provider}`
`ProviderWebhookController` (C.1) → `ProviderEventService#receive` → `WebhookTranslators` registry
(C.2) → `StripeEventTranslator`: **signature first**, then map (C.3) → inbox de-duplication (C.4) →
find the payment by *our* attempt id (C.5) → `Payment#report` decides: applied / already known / stale
/ conflict (C.6) → save and mark handled (C.7) → receipt → HTTP status (C.8).

### FLOW D: reconciliation
`ReconciliationScheduler` or `POST /admin/reconcile` (D.1) → `ReconciliationService#run`:
UNKNOWN/PROCESSING authorizations → **ask** the provider with `getStatus`, never re-send (D.2, D.3);
unknown operations → **re-send** with the same operation id (D.4); late automatic captures (D.5).

### FLOW S: startup
`PaymentPlatformApplication` (S.1) → `PaymentProperties` binds `application.yml` (S.2) →
`IntegrationConfig` builds every enabled provider from its `ProviderFactory` plug-in and wraps it in
`breaker(retry(call))` (S.3) → `RoutingConfig` builds the rules and fails fast on typos (S.4) →
`schema.sql`, Spring Data repositories and `PersistenceConfig` (S.5) → services and controllers (S.6).

---

## 3. Where to start: a reading order

Each step builds on the one before it. Read the `package-info.java` of a package before its classes.

1. **`com/designpatterns/level09/package-info.java`**, the map, then **`application.yml`**: which
   providers exist, routing rules, merchants and their credentials.
2. **The model, bottom-up:** `Money` → `PaymentId` / `ProviderId` → `PaymentStatus` →
   `PaymentOperation` → **`Payment`** (draw its state machine on paper from the class comment).
   Then read **`model/PaymentTest`**: tests are the rules written as examples.
3. **The provider contract:** `integration/gateway/package-info` → `GatewayResult` →
   **`FailureCategory`** (the table in its comment is the heart of the safety rules) →
   `ProviderAdapter` + `capability/*` → `ProviderGateway` / `DefaultProviderGateway`.
4. **One provider end to end:** `provider/stripe/`, `StripeGateway` → `StripeMapper` → `StripeApi`,
   then `StripeMapperTest` (recorded JSON, no network).
5. **FLOW A through the service layer:** `PaymentService` → `ProviderRoutingService` →
   `AuthorizationOrchestrator` → `GatewayResultApplier`. Then **`PaymentServiceTest`**, which uses the
   in-memory fakes in `test/.../support/` and `ScriptedGateway` to script provider answers.
6. **Persistence:** `persistence/package-info` → `PaymentRepository` → `persistence/jpa/package-info`
   → `PaymentEntity` → `SpringDataPayments` → **`JpaPaymentRepository`** (the version check under a row
   lock). Then `JpaPersistenceTest` (the 8-thread race tests).
7. **The edge:** `PaymentController` → `IdempotentRequestExecutor` → `ApiExceptionHandler`. Then
   **`e2e/PlatformApiTest`**: the whole stack against real simulators.
8. **The remaining flows:** B (`PaymentOperationService`), C (`ProviderWebhookController`,
   `ProviderEventService`), D (`ReconciliationService`).
9. **Wiring last:** `config/*`. By now you know every bean it connects.
10. **The guard rails:** `architecture/LayeredArchitectureTest`, which turns each rule into code.

---

## 4. How to analyse the code (techniques that work)

**a. Run one test in the debugger and step through it.** This is the fastest way to see a flow for real.
Put a breakpoint in `PaymentController#authorize`, then run in debug mode:
`e2e/PlatformApiTest#stripe_authorize100_capture60_refund30_refund31Refused_refund30`.
Step into each call, and watch the `Payment` object's `status`, `version` and `newEvents` change.

**b. Run a single test from the command line:**
```bash
./mvnw -f levels/09-layered-architecture/pom.xml test -Dtest=PaymentServiceTest
./mvnw -f levels/09-layered-architecture/pom.xml test -Dtest='PaymentServiceTest#unknownOutcomeNeverFallsBack'
```

**c. Read the logs of one request.** Every log line shows `[cid=... aid=...]` (the correlation id and the
attempt id). Pick one `cid` in the test output and read only the lines that have it.

**d. Run the real application** (two terminals, from the repo root):
```bash
./mvnw -q install -DskipTests
./mvnw -q exec:java -pl tools/provider-simulators            # simulators on 4101–4107
./mvnw -q spring-boot:run -pl levels/09-layered-architecture  # app on :8080
```
```bash
curl -i -X POST localhost:8080/payments \
  -H 'X-Merchant-Id: acme' -H 'Idempotency-Key: k-1' -H 'Content-Type: application/json' \
  -d '{"customerId":"c-1","amountMinor":1999,"currency":"EUR"}'
# send the SAME command again: you get the stored answer and the header Idempotent-Replayed: true
curl -i localhost:8080/payments/<id>/history -H 'X-Merchant-Id: acme'   # the audit trail (domain events)
curl localhost:4101/sim/ledger                                          # what the simulator REALLY charged
```
Inject a fault to see the safety rules at work, for example a timeout that leaves the payment UNKNOWN and
then `POST /admin/reconcile`. The simulators' README lists the faults.

**e. Ask "who calls this?" and "who implements this?"** In the IDE, use *Find Usages* on
`ProviderGateway#call` (every provider call in the system) and *Go to Implementation* on
`PaymentRepository` (the real JPA version and the test fake).

**f. Break a rule on purpose and watch what stops you.** For example:
- add `@Transactional` to a service method, then run `LayeredArchitectureTest`;
- import a `persistence.jpa` class in a service, same test;
- add a value to `GatewayResult`: `GatewayResultApplier` stops compiling (exhaustive `switch`);
- let `StripeGateway` drop `Refunder` but keep `REFUND` in its descriptor: the app refuses to start.

**g. Compare with Level 8.** Open the same use case in both levels, e.g. `AuthorizationOrchestrator`
(L9) vs `PaymentOrchestrator` (L8). The logic is the same; what changed is *who owns which interface*.
That difference is the whole point of this level.

---

## 5. Pattern index: what, where, why

| Pattern | Where | Why here | Use it when / avoid when |
|---|---|---|---|
| Layered architecture | whole module | simple mental model: call downwards only | most business apps / when the core must survive many changing technologies, hexagonal (L8) protects it better |
| Value Object | `Money`, ids | no floats for money; a `PaymentId` can't be swapped with a `MerchantId` | any concept compared by value / not for things with identity and a lifecycle |
| Aggregate + State machine | `Payment` | one place that guards every rule and transition | data with invariants / not for plain read models |
| Static Factory Method | `Payment.create` / `restore` | two ways to build, with names that say why | several construction intentions |
| Domain Events | `PaymentEvent`, audit trail | history saved with the state, atomically | auditing, reacting to changes |
| Repository | `persistence/*Repository` | services see collections, not SQL | aggregates loaded and saved as a whole |
| Data Mapper | `Jpa*Repository` ⇄ `*Entity` | the model stays free of JPA | rich models / for simple CRUD, the entity can be the model |
| Optimistic concurrency + row lock | `JpaPaymentRepository#writePayment` | two refunds racing: exactly one wins | rare conflicts, short writes |
| Write-ahead | `AuthorizationOrchestrator#writeAhead`, `PaymentOperationService#run` | never lose track of a call we started | before any irreversible external call |
| Idempotency key / Execute Around | `IdempotentRequestExecutor` | a client retry never pays twice | every money-moving command |
| Inbox / Idempotent Consumer | `ProviderEventService#handle` | webhooks arrive twice | any at-least-once message source |
| Adapter + Anti-Corruption Layer | `provider/*/…Gateway`, `…Mapper` | provider words stop at the boundary | integrating foreign APIs |
| Interface Segregation | `gateway/capability/*` | a provider implements only what it can | optional abilities |
| Facade + Registry | `ProviderGateway`, `WebhookTranslators` | one entry point; lookup by id | many interchangeable plug-ins |
| Plug-in Factory | `ProviderFactory` + `@Component` | adding a provider changes no other folder (Open/Closed) | extension points |
| Strategy (+ chain) | `RoutingRule`, `ProviderEventTranslator` | swap an algorithm per case | behaviour that varies by case |
| Decorator | `breaker(retry(call))` | add retry and breaker without touching adapters | layering cross-cutting behaviour |
| Circuit Breaker | `CircuitBreakerPolicy` | stop hammering a provider that is down | remote calls |
| Sealed result types | `GatewayResult`, `WebhookReceipt`, `IdempotencyClaim` | the compiler makes you handle every case | expected outcomes that aren't errors |
| DTO + Exception Translation | `presentation/rest/dto`, `ApiExceptionHandler` | the API shape is independent of the model | every public API |
| Intercepting Filter | `CorrelationIdFilter` | applies to every request | cross-cutting HTTP concerns |
| Fitness function | `LayeredArchitectureTest` | architecture rules checked on every build | any rule you want to stay true |

---

## 6. Annotation cheat sheet

| Annotation | Meaning in simple words | Example |
|---|---|---|
| `@SpringBootApplication` | start Spring Boot, auto-configure, scan this package and below | `PaymentPlatformApplication` |
| `@Component` / `@Service` / `@Repository` / `@RestController` | "Spring, create one of these and inject it where needed"; the name says the role. `@Repository` also translates DB exceptions | everywhere |
| `@Configuration` + `@Bean` | a recipe class; each `@Bean` method's return value becomes a bean | `config/*` |
| `@ConfigurationProperties` | fill a typed record from `application.yml` | `PaymentProperties` |
| `@Value("${…}")` | inject one setting | `StripeEventTranslator` |
| `@Autowired` | on a constructor: "use this one" (only needed when there are several) | `ProviderRoutingService` |
| `@PostMapping` / `@GetMapping` | map a URL + HTTP method to a method | `PaymentController` |
| `@PathVariable` / `@RequestHeader` / `@RequestBody` | take a value from the URL / a header / the body | `PaymentController` |
| `@RestControllerAdvice` + `@ExceptionHandler` | one place that turns exceptions into responses | `ApiExceptionHandler` |
| `@Scheduled` + `@EnableScheduling` | run a method on a timer | `ReconciliationScheduler` |
| `@ConditionalOnProperty` | create the bean only if a setting says so | `ReconciliationScheduler` |
| `@Entity` / `@Table` / `@Id` / `@Column` / `@IdClass` | map a class to a table row | `persistence/jpa/*Entity` |
| `@Transient` | a field JPA must not store | `PaymentEntity.isNew` |
| `@PostLoad` / `@PostPersist` | JPA callbacks after load / insert | `PaymentEntity#markNotNew` |
| `@GeneratedValue(IDENTITY)` | the database assigns the id | `PaymentEventEntity` |
| `@Query` / `@Modifying` / `@Lock` | own JPQL / an UPDATE query / `SELECT … FOR UPDATE` | `SpringData*` |
| `@Transactional` | open a transaction around the method. **Allowed only in persistence**, never in services | `SpringDataInbox#markHandled` |
| Tests: `@SpringBootTest`, `@DataJpaTest` | whole app (e2e only) / only the JPA slice (persistence only) | `e2e/*`, `JpaPersistenceTest` |

---

## 7. Check yourself (small exercises)

1. Explain, without looking, why a timeout after sending a request must not fall back to another
   provider. Find the line that enforces it (hint: `FailureCategory`, `AuthorizationOrchestrator`).
2. Two refunds of 600 arrive at the same time on a captured 1000 payment. Follow FLOW B and say exactly
   where the second one stops, and what HTTP status it gets.
3. A webhook says "authorized" *before* Stripe's HTTP answer reaches us. Follow FLOW C and A.12: what does
   each side do?
4. Add a fake provider `demo` that can only `CHARGE`. Which folders do you touch? (The answer should be:
   one new folder plus one line in `application.yml`.)
5. Write down, in your `NOTES.md`, three things that were easier in the layered version and three that
   were easier in hexagonal Level 8.
