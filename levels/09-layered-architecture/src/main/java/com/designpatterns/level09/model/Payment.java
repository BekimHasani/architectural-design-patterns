package com.designpatterns.level09.model;

import com.designpatterns.level09.model.exceptions.IllegalPaymentTransitionException;
import com.designpatterns.level09.model.exceptions.PaymentRuleViolationException;
import com.designpatterns.level09.model.PaymentOperation.Type;
import com.designpatterns.level09.model.PaymentOperation.Status;

import java.net.URI;
import java.util.*;

import static com.designpatterns.level09.model.PaymentStatus.*;

/**
 * THE AGGREGATE ROOT: one payment, its provider operations (capture, cancel, refund) and the events
 * it produced. Every change goes through a method of this class, which checks the rule first. No
 * setters, so no rule can be skipped. Pure Java: no JPA, no Spring (it is stored by
 * {@code persistence.jpa.JpaPaymentRepository} through a separate entity: DATA MAPPER).
 *
 * <p><b>STATE MACHINE</b> (main paths; {@code require(...)} in each method lists the allowed "from" states):
 * <pre>
 *  CREATED ──startAttempt──► PENDING ──authorized──► AUTHORIZED ──capture──► CAPTURED ──refund──► REFUNDED
 *     ▲                        │  │ │                    └──cancel──► CANCELLED
 *     └──nothingCharged────────┘  │ ├─declined──► DECLINED
 *        (safe fallback)          │ ├─requiresAction──► ACTION_REQUIRED (customer must act, then complete)
 *                                 │ ├─processing──► PROCESSING (answer comes later by webhook)
 *                                 │ └─charged──► CAPTURED (direct-charge providers, one step)
 *                                 └─outcomeUnknown──► UNKNOWN (timeout: FLOW D will ask the provider)
 * </pre>
 *
 * <p><b>Used in every flow:</b> FLOW A ({@code create}, {@code startAttempt}, {@code authorized}, ...),
 * FLOW B ({@code startCapture}, {@code operationSucceeded}, ...), FLOW C ({@code report}), FLOW D.
 *
 * <p><b>Two STATIC FACTORY METHODS</b> instead of public constructors: {@code create} for a new payment
 * (rules + "PaymentCreated" event) and {@code restore} for one loaded from the database (no rules, no
 * events: it already happened).
 *
 * <p><b>{@code version}</b> is the optimistic-locking counter: the repository only saves this copy if the
 * stored version is still the one we loaded. <b>{@code newEvents}</b> are the DOMAIN EVENTS produced since
 * loading; the repository writes them as the audit trail in the same transaction as the state.
 */
public final class Payment {

    private final PaymentId id;
    private final MerchantId merchantId;
    private final String customerId;
    private final Money amount;
    private final CaptureMode captureMode;
    private final List<PaymentOperation> operations;
    private final int version;
    private final List<PaymentEvent> newEvents = new ArrayList<>();
    private PaymentStatus status;
    private ProviderId providerId;
    private String providerRef;
    private String attemptId;
    private URI redirectUrl;
    private String reason;
    private String actor = "system";

    private Payment(PaymentId id, MerchantId merchantId, String customerId, Money amount,
                    CaptureMode captureMode, PaymentStatus status,
                    ProviderId providerId, String providerRef,
                    String attemptId, URI redirectUrl, String reason,
                    List<PaymentOperation> operations, int version) {
        this.id = Objects.requireNonNull(id, "id");
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.captureMode = Objects.requireNonNull(captureMode, "captureMode");
        this.status = Objects.requireNonNull(status, "status");
        this.providerId = providerId;
        this.providerRef = providerRef;
        this.attemptId = attemptId;
        this.redirectUrl = redirectUrl;
        this.reason = reason;
        this.operations = new ArrayList<>(operations);
        this.version = version;
    }

    public static Payment create(PaymentId id, MerchantId merchantId, String customerId, Money amount) {
        return create(id, merchantId, customerId, amount, CaptureMode.MANUAL);
    }

    public static Payment create(PaymentId id, MerchantId merchantId, String customerId, Money amount, CaptureMode mode) {
        if (amount.isZero()) {
            throw new PaymentRuleViolationException("A payment needs an amount");
        }
        Payment payment = new Payment(id, merchantId, customerId, amount, mode, PaymentStatus.CREATED, null, null, null, null,
                null, List.of(), 0);
        payment.actor = "merchant:" + merchantId;
        payment.event("PaymentCreated", PaymentStatus.CREATED, amount, null);
        return payment;
    }

    /** For repositories only: rebuilds a stored payment without running transitions or producing events. */
    public static Payment restore(PaymentId id, MerchantId merchantId, String customerId, Money amount,
                                  CaptureMode captureMode, PaymentStatus status, ProviderId providerId, String providerRef, String attemptId,
                                  URI redirectUrl, String reason, List<PaymentOperation> operations, int version) {
        return new Payment(id, merchantId, customerId, amount, captureMode, status, providerId, providerRef, attemptId, redirectUrl,
                reason, operations, version);
    }

    /** Who is acting from now on, for the audit trail. */
    public Payment actingAs(String who) {
        this.actor = who;
        return this;
    }

    // ---- authorization -----------------------------------------------------------------------

    /** FLOW A.9: we are about to call {@code provider}; {@code newAttemptId} is OUR id the provider will echo back. */
    public void startAttempt(String newAttemptId, ProviderId provider) {
        require("startAttempt", PaymentStatus.CREATED);
        this.attemptId = Objects.requireNonNull(newAttemptId);
        this.providerId = Objects.requireNonNull(provider);
        transition("AttemptStarted", PaymentStatus.PENDING, null, "provider " + provider);
    }

    public void startCompletion(String newAttemptId) {
        require("startCompletion", PaymentStatus.ACTION_REQUIRED);
        this.attemptId = Objects.requireNonNull(newAttemptId);
        transition("CompletionStarted", PaymentStatus.PENDING, null, null);
    }

    public void authorized(String ref) {
        require("authorized", PaymentStatus.PENDING, PaymentStatus.UNKNOWN, PaymentStatus.PROCESSING,
                PaymentStatus.ACTION_REQUIRED);
        this.providerRef = ref;
        this.redirectUrl = null;
        transition("PaymentAuthorized", AUTHORIZED, amount, null);
    }

    /** Direct-charge providers: authorize and capture are one step, so the capture is recorded as done. */
    public void charged(String ref) {
        require("charged", PaymentStatus.PENDING, PaymentStatus.UNKNOWN, PaymentStatus.PROCESSING,
                PaymentStatus.ACTION_REQUIRED);
        this.providerRef = ref;
        this.redirectUrl = null;
        operations.add(new PaymentOperation(attemptId, Type.CAPTURE, amount, Status.SUCCEEDED, ref, null));
        transition("PaymentCaptured", CAPTURED, amount, "charged directly");
    }

    public void declined(DeclineReason declineReason) {
        require("declined", PaymentStatus.PENDING, PaymentStatus.UNKNOWN, PaymentStatus.PROCESSING);
        this.reason = declineReason.name();
        transition("PaymentDeclined", DECLINED, null, reason);
    }

    public void requiresAction(String ref, URI url) {
        require("requiresAction", PaymentStatus.PENDING, PaymentStatus.UNKNOWN);
        this.providerRef = ref;
        this.redirectUrl = Objects.requireNonNull(url);
        transition("CustomerActionRequired", PaymentStatus.ACTION_REQUIRED, null, null);
    }

    public void processing(String ref) {
        require("processing", PaymentStatus.PENDING, PaymentStatus.UNKNOWN);
        this.providerRef = ref;
        transition("PaymentProcessing", PaymentStatus.PROCESSING, null, null);
    }

    public void nothingCharged() {
        require("nothingCharged", PaymentStatus.PENDING);
        transition("AttemptFailedNothingCharged", PaymentStatus.CREATED, null, "provider " + providerId);
    }

    public void completionNotDone() {
        require("completionNotDone", PaymentStatus.PENDING);
        transition("CompletionFailedNothingLost", PaymentStatus.ACTION_REQUIRED, null, null);
    }

    public void failed(String why) {
        require("failed", PaymentStatus.CREATED, PaymentStatus.PENDING, PaymentStatus.UNKNOWN);
        this.reason = why;
        transition("PaymentFailed", PaymentStatus.FAILED, null, why);
    }

    public void outcomeUnknown() {
        require("outcomeUnknown", PaymentStatus.PENDING);
        transition("OutcomeUnknown", PaymentStatus.UNKNOWN, null, "provider " + providerId);
    }

    // ---- capture, cancel, refund --------------------------------------------------------------

    /**
     * FLOW B.3: the rules for a capture are checked here, in the model, before anything is stored or sent.
     * The returned operation is IN_FLIGHT: its amount counts as reserved until the provider answers.
     *
     * @param requested null means the full authorized amount
     */
    public PaymentOperation startCapture(String operationId, Money requested) {
        require("capture", AUTHORIZED);
        requireNoOpenOperation();
        Money toCapture = requested == null ? amount : requested;
        if (toCapture.isZero() || toCapture.isGreaterThan(amount)) {
            throw new PaymentRuleViolationException("Cannot capture " + toCapture.amountMinor() + " of an authorization of "
                    + amount.amountMinor());
        }
        return start(new PaymentOperation(operationId, Type.CAPTURE, toCapture, Status.IN_FLIGHT, null, null));
    }

    public PaymentOperation startCancel(String operationId) {
        require("cancel", AUTHORIZED);
        requireNoOpenOperation();
        return start(new PaymentOperation(operationId, Type.CANCEL, null, Status.IN_FLIGHT, null, null));
    }

    /** @param requested null means everything still refundable */
    public PaymentOperation startRefund(String operationId, Money requested) {
        return startRefund(operationId, requested, RefundTerms.ANY);
    }

    /** @param terms the rules of the provider that holds this money */
    public PaymentOperation startRefund(String operationId, Money requested, RefundTerms terms) {
        require("refund", CAPTURED);
        Money refundable = refundable();
        Money toRefund = requested == null ? refundable : requested;
        long refundsSoFar = operations.stream().filter(op -> op.counts(Type.REFUND)).count();
        if (terms.maxRefunds() != null && refundsSoFar >= terms.maxRefunds()) {
            throw new PaymentRuleViolationException("This provider allows only " + terms.maxRefunds() + " refund(s)");
        }
        if (!terms.partialAllowed() && !toRefund.equals(captured())) {
            throw new PaymentRuleViolationException("This provider only refunds in full");
        }
        if (toRefund.isZero() || toRefund.isGreaterThan(refundable)) {
            throw new PaymentRuleViolationException("Cannot refund " + toRefund.amountMinor() + ": only "
                    + refundable.amountMinor() + " is refundable");
        }
        return start(new PaymentOperation(operationId, Type.REFUND, toRefund, Status.IN_FLIGHT, null, null));
    }

    public void operationSucceeded(String operationId, String ref) {
        PaymentOperation done = replace(operation(operationId).with(Status.SUCCEEDED, ref, null));
        switch (done.type()) {
            case CAPTURE -> transition("PaymentCaptured", CAPTURED, done.amount(), null);
            case CANCEL -> transition("PaymentCancelled", PaymentStatus.CANCELLED, null, null);
            case REFUND -> {
                boolean nothingLeft = refundable().isZero() && operations.stream().noneMatch(PaymentOperation::open);
                transition("PaymentRefunded", nothingLeft ? PaymentStatus.REFUNDED : status, done.amount(), null);
            }
        }
    }

    /** Provably not done: the reservation is released. */
    public void operationFailed(String operationId, String why) {
        PaymentOperation failed = replace(operation(operationId).with(Status.FAILED, null, why));
        event("OperationFailed", status, failed.amount(), failed.type() + ": " + why);
    }

    /** The reservation stays: the money may have moved. */
    public void operationUnknown(String operationId) {
        PaymentOperation unknown = replace(operation(operationId).with(Status.UNKNOWN, null, null));
        event("OperationOutcomeUnknown", status, unknown.amount(), unknown.type().name());
    }

    // ---- provider news, in any order ----------------------------------------------------------

    /**
     * FLOW C.6: a provider reports news (webhook). Webhooks arrive late, twice, or out of order, so this
     * never throws for old news: the state machine decides (see {@link ReportResult}) whether the news is
     * new (APPLIED), already known, older than what we know (STALE), or contradicts us (CONFLICT).
     */
    public ReportResult report(ReportedOutcome outcome, String ref) {
        return switch (outcome) {
            case PROCESSING -> switch (status) {
                case PENDING, UNKNOWN -> apply(() -> processing(ref));
                case PROCESSING -> ReportResult.ALREADY_KNOWN;
                default -> ReportResult.STALE;
            };
            case AUTHORIZED -> switch (status) {
                case PENDING, UNKNOWN, PROCESSING, ACTION_REQUIRED -> apply(() -> authorized(ref));
                case AUTHORIZED -> ReportResult.ALREADY_KNOWN;
                case CAPTURED, CANCELLED, REFUNDED -> ReportResult.STALE;
                case CREATED, DECLINED, FAILED -> ReportResult.CONFLICT;
            };
            case CAPTURED -> switch (status) {
                case PENDING, UNKNOWN, PROCESSING, ACTION_REQUIRED -> apply(() -> charged(ref));
                case AUTHORIZED, CAPTURED, REFUNDED -> ReportResult.ALREADY_KNOWN; // our capture operation records it
                case CREATED, CANCELLED, DECLINED, FAILED -> ReportResult.CONFLICT;
            };
            case DECLINED -> switch (status) {
                case PENDING, UNKNOWN, PROCESSING -> apply(() -> declined(DeclineReason.GENERIC));
                case DECLINED, FAILED -> ReportResult.ALREADY_KNOWN;
                case ACTION_REQUIRED, CREATED -> ReportResult.STALE;
                case AUTHORIZED, CAPTURED, CANCELLED, REFUNDED -> ReportResult.CONFLICT;
            };
        };
    }

    // ---- derived amounts ----------------------------------------------------------------------

    public Money captured() {
        return sum(Type.CAPTURE, true);
    }

    public Money refunded() {
        return sum(Type.REFUND, true);
    }

    /** Captured, minus refunds done, minus refunds in flight or unknown (they may have moved money). */
    public Money refundable() {
        return captured().minus(sum(Type.REFUND, false));
    }

    private Money sum(Type type, boolean settledOnly) {
        return operations.stream()
                .filter(op -> settledOnly ? op.settled(type) : op.counts(type))
                .map(PaymentOperation::amount)
                .reduce(Money.zero(amount.currency()), Money::plus);
    }

    /** What a refund must point at: the capture's reference where the provider has one. */
    public String refundTarget() {
        return operations.stream().filter(op -> op.settled(Type.CAPTURE)).map(PaymentOperation::providerRef)
                .filter(Objects::nonNull).findFirst().orElse(providerRef);
    }

    // ---- internals ----------------------------------------------------------------------------

    private PaymentOperation start(PaymentOperation operation) {
        operations.add(operation);
        event(operation.type() + "Requested", status, operation.amount(), operation.operationId());
        return operation;
    }

    private void requireNoOpenOperation() {
        if (operations.stream().anyMatch(PaymentOperation::open)) {
            throw new PaymentRuleViolationException("Another operation on this payment is not finished");
        }
    }

    private PaymentOperation operation(String operationId) {
        return operations.stream().filter(op -> op.operationId().equals(operationId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No operation " + operationId));
    }

    private PaymentOperation replace(PaymentOperation updated) {
        operations.replaceAll(op -> op.operationId().equals(updated.operationId()) ? updated : op);
        return updated;
    }

    private void transition(String eventType, PaymentStatus to, Money money, String detail) {
        PaymentStatus from = status;
        status = to;
        newEvents.add(new PaymentEvent(eventType, from, to, money == null ? null : money.amountMinor(), actor, detail));
    }

    private void event(String eventType, PaymentStatus at, Money money, String detail) {
        newEvents.add(new PaymentEvent(eventType, at, at, money == null ? null : money.amountMinor(), actor, detail));
    }

    private void require(String action, PaymentStatus... allowed) {
        if (Arrays.stream(allowed).noneMatch(s -> s == status)) {
            throw new IllegalPaymentTransitionException(id, status, action);
        }
    }

    private static ReportResult apply(Runnable transition) {
        transition.run();
        return ReportResult.APPLIED;
    }

    // ---- reads --------------------------------------------------------------------------------

    public PaymentId id() {
        return id;
    }

    public MerchantId merchantId() {
        return merchantId;
    }

    public String customerId() {
        return customerId;
    }

    public Money amount() {
        return amount;
    }

    public CaptureMode captureMode() {
        return captureMode;
    }

    public PaymentStatus status() {
        return status;
    }

    public ProviderId providerId() {
        return providerId;
    }

    public String providerRef() {
        return providerRef;
    }

    public String attemptId() {
        return attemptId;
    }

    public URI redirectUrl() {
        return redirectUrl;
    }

    public String reason() {
        return reason;
    }

    public int version() {
        return version;
    }

    public String actor() {
        return actor;
    }

    public List<PaymentOperation> operations() {
        return List.copyOf(operations);
    }

    public Optional<PaymentOperation> findOperation(String operationId) {
        return operations.stream().filter(op -> op.operationId().equals(operationId)).findFirst();
    }

    /** New events since load, for the repository to store in the same transaction as the state. */
    public List<PaymentEvent> newEvents() {
        return List.copyOf(newEvents);
    }
}
