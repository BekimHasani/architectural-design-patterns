package com.designpatterns.level01.payments.application.commands;

import com.designpatterns.level01.payments.application.capabilities.Canceller;
import com.designpatterns.level01.payments.application.capabilities.Capability;
import com.designpatterns.level01.payments.application.capabilities.Capturer;
import com.designpatterns.level01.payments.application.capabilities.Refunder;
import com.designpatterns.level01.payments.application.ports.ConcurrentUpdateException;
import com.designpatterns.level01.payments.application.ports.GatewayRegistry;
import com.designpatterns.level01.payments.application.ports.GatewayResult;
import com.designpatterns.level01.payments.application.ports.MerchantCredentials;
import com.designpatterns.level01.payments.application.ports.OperationRequest;
import com.designpatterns.level01.payments.application.ports.PaymentAttemptLog;
import com.designpatterns.level01.payments.application.ports.PaymentRepository;
import com.designpatterns.level01.payments.domain.MerchantId;
import com.designpatterns.level01.payments.domain.Money;
import com.designpatterns.level01.payments.domain.Payment;
import com.designpatterns.level01.payments.domain.PaymentId;
import com.designpatterns.level01.payments.domain.PaymentOperation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Commands on an authorized or captured payment. All three follow the same steps:
 * <ol>
 *   <li>the aggregate checks the rules and records the operation; COMMIT (this reserves the amount,
 *       and a concurrent request loses HERE, on the optimistic lock, before calling anyone);</li>
 *   <li>call the provider that took the payment, with the operation id as idempotency key;</li>
 *   <li>record the outcome; COMMIT.</li>
 * </ol>
 */
public final class PaymentOperations {

    private static final int MAX_TRIES = 3;

    private final PaymentRepository payments;
    private final PaymentAttemptLog attempts;
    private final GatewayRegistry gateways;
    private final MerchantCredentials credentials;

    public PaymentOperations(PaymentRepository payments, PaymentAttemptLog attempts, GatewayRegistry gateways,
                             MerchantCredentials credentials) {
        this.payments = Objects.requireNonNull(payments, "payments");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.gateways = Objects.requireNonNull(gateways, "gateways");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
    }

    /** @param amount null for the full authorized amount */
    public PaymentResult capture(MerchantId merchantId, PaymentId paymentId, Money amount) {
        Payment payment = load(merchantId, paymentId);
        requireCapability(payment, Capability.CAPTURE);
        PaymentOperation operation = payment.startCapture(newOperationId(), amount);
        return run(payment, operation, payment.providerRef());
    }

    public PaymentResult cancel(MerchantId merchantId, PaymentId paymentId) {
        Payment payment = load(merchantId, paymentId);
        requireCapability(payment, Capability.CANCEL);
        PaymentOperation operation = payment.startCancel(newOperationId());
        return run(payment, operation, payment.providerRef());
    }

    /** @param amount null for everything still refundable */
    public PaymentResult refund(MerchantId merchantId, PaymentId paymentId, Money amount) {
        Payment payment = load(merchantId, paymentId);
        requireCapability(payment, Capability.REFUND);
        PaymentOperation operation = payment.startRefund(newOperationId(), amount,
                gateways.descriptor(payment.providerId()).refundTerms());
        return run(payment, operation, payment.refundTarget());
    }

    /**
     * Reconciliation: re-sends an operation whose outcome is unknown. The same operation id means
     * the provider replays its answer instead of moving money twice.
     *
     * @return true if the outcome is now known
     */
    public boolean resend(Payment payment, PaymentOperation operation) {
        String target = operation.type() == PaymentOperation.Type.REFUND ? payment.refundTarget() : payment.providerRef();
        GatewayResult result = send(payment, operation, request(payment, operation, target));
        if (isUnknown(result)) {
            return false;
        }
        record(payment, operation.operationId(), result, "reconciliation");
        return true;
    }

    /** What can be done with this payment right now, at its provider. */
    public List<String> allowedActions(Payment payment) {
        return switch (payment.status()) {
            case AUTHORIZED -> actions(payment, Capability.CAPTURE, Capability.CANCEL);
            case CAPTURED -> payment.refundable().isZero() ? List.of() : actions(payment, Capability.REFUND);
            default -> List.of();
        };
    }

    private List<String> actions(Payment payment, Capability... wanted) {
        return Arrays.stream(wanted)
                .filter(gateways.descriptor(payment.providerId())::supports)
                .map(capability -> capability.name().toLowerCase())
                .toList();
    }

    /** Refused here, in our words, before anything is stored or sent. */
    private void requireCapability(Payment payment, Capability capability) {
        if (!gateways.descriptor(payment.providerId()).supports(capability)) {
            throw new OperationNotSupportedException(payment.providerId(), capability);
        }
    }

    private GatewayResult send(Payment payment, PaymentOperation operation, OperationRequest request) {
        return switch (operation.type()) {
            case CAPTURE -> gateways.call(payment.providerId(), Capturer.class, g -> g.capture(request));
            case CANCEL -> gateways.call(payment.providerId(), Canceller.class, g -> g.cancel(request));
            case REFUND -> gateways.call(payment.providerId(), Refunder.class, g -> g.refund(request));
        };
    }

    private PaymentResult run(Payment payment, PaymentOperation operation, String target) {
        payments.save(payment); // write-ahead: reserves the amount, or loses the race
        GatewayResult result = send(payment, operation, request(payment, operation, target));
        Payment stored = record(payment, operation.operationId(), result, "merchant:" + payment.merchantId());
        return new PaymentResult(stored, attempts.forPayment(payment.id()), operation.operationId());
    }

    /** Another operation or a webhook may have saved meanwhile: always apply on the latest copy. */
    private Payment record(Payment payment, String operationId, GatewayResult result, String actor) {
        for (int attempt = 1; ; attempt++) {
            Payment latest = payments.find(payment.id()).orElseThrow().actingAs(actor);
            apply(latest, operationId, result);
            try {
                return payments.save(latest);
            } catch (ConcurrentUpdateException raced) {
                if (attempt == MAX_TRIES) {
                    throw raced;
                }
            }
        }
    }

    private static void apply(Payment payment, String operationId, GatewayResult result) {
        switch (result) {
            case GatewayResult.Done done -> payment.operationSucceeded(operationId, done.providerRef());
            case GatewayResult.Declined declined -> payment.operationFailed(operationId, declined.reason().name());
            case GatewayResult.Failed failed when failed.category().outcomeUnknown() -> payment.operationUnknown(operationId);
            case GatewayResult.Failed failed -> payment.operationFailed(operationId, failed.category().name());
            default -> payment.operationUnknown(operationId); // an answer that makes no sense for this call
        }
    }

    private static boolean isUnknown(GatewayResult result) {
        return result instanceof GatewayResult.Failed failed
                && (failed.category().outcomeUnknown() || failed.category().retryable());
    }

    private OperationRequest request(Payment payment, PaymentOperation operation, String target) {
        return new OperationRequest(target, operation.amount(), operation.operationId(),
                credentials.find(payment.merchantId(), payment.providerId()).orElseThrow());
    }

    private Payment load(MerchantId merchantId, PaymentId paymentId) {
        return payments.find(paymentId)
                .filter(p -> p.merchantId().equals(merchantId))
                .orElseThrow(() -> new UnknownPaymentException(paymentId))
                .actingAs("merchant:" + merchantId);
    }

    private static String newOperationId() {
        return "op_" + UUID.randomUUID();
    }
}
