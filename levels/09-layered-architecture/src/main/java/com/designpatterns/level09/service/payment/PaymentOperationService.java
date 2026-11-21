package com.designpatterns.level09.service.payment;

import org.springframework.stereotype.Service;

import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.integration.gateway.OperationRequest;
import com.designpatterns.level09.integration.gateway.ProviderGateway;
import com.designpatterns.level09.integration.gateway.capability.Canceller;
import com.designpatterns.level09.integration.gateway.capability.Capturer;
import com.designpatterns.level09.integration.gateway.capability.Refunder;
import com.designpatterns.level09.model.Capability;
import com.designpatterns.level09.model.MerchantId;
import com.designpatterns.level09.model.Money;
import com.designpatterns.level09.model.Payment;
import com.designpatterns.level09.model.PaymentId;
import com.designpatterns.level09.model.PaymentOperation;
import com.designpatterns.level09.model.exceptions.ConcurrentUpdateException;
import com.designpatterns.level09.persistence.PaymentAttemptRepository;
import com.designpatterns.level09.persistence.PaymentRepository;
import com.designpatterns.level09.service.exception.OperationNotSupportedException;
import com.designpatterns.level09.service.exception.UnknownPaymentException;
import com.designpatterns.level09.service.merchant.MerchantService;


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
@Service
public class PaymentOperationService {

    private static final int MAX_TRIES = 3;

    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final ProviderGateway gateways;
    private final MerchantService merchants;

    public PaymentOperationService(PaymentRepository payments, PaymentAttemptRepository attempts, ProviderGateway gateways,
                             MerchantService merchants) {
        this.payments = Objects.requireNonNull(payments, "payments");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.gateways = Objects.requireNonNull(gateways, "gateways");
        this.merchants = Objects.requireNonNull(merchants, "merchants");
    }

    /** @param amount null for the full authorized amount */
    public PaymentResult capture(MerchantId merchantId, PaymentId paymentId, Money amount) {
        Payment payment = load(merchantId, paymentId);              // FLOW B.2: load, check ownership
        requireCapability(payment, Capability.CAPTURE);             // FLOW B.2: can this provider capture?
        PaymentOperation operation = payment.startCapture(newOperationId(), amount); // FLOW B.3: rules + reserve
        return run(payment, operation, payment.providerRef());     // FLOW B.4 – B.6
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
        if (GatewayResultApplier.stillUnknown(result)) {
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

    /** Picks the capability interface for the operation type; exhaustive switch over the enum. */
    private GatewayResult send(Payment payment, PaymentOperation operation, OperationRequest request) {
        return switch (operation.type()) {
            case CAPTURE -> gateways.call(payment.providerId(), Capturer.class, g -> g.capture(request));
            case CANCEL -> gateways.call(payment.providerId(), Canceller.class, g -> g.cancel(request));
            case REFUND -> gateways.call(payment.providerId(), Refunder.class, g -> g.refund(request));
        };
    }

    private PaymentResult run(Payment payment, PaymentOperation operation, String target) {
        // FLOW B.4: write-ahead: reserves the amount, or loses the race. Two refunds of 600 on a 1000
        // payment: both pass the rule in memory, but only one save wins; the other gets 409 and NOTHING
        // was sent to the provider.
        payments.save(payment);
        // FLOW B.5: call the provider (outside any transaction). The operation id is sent as the provider's
        // idempotency key, so a resend (FLOW D) can never move the money twice.
        GatewayResult result = send(payment, operation, request(payment, operation, target));
        // FLOW B.6: record the outcome on the LATEST copy of the payment
        Payment stored = record(payment, operation.operationId(), result, "merchant:" + payment.merchantId());
        return new PaymentResult(stored, attempts.forPayment(payment.id()), operation.operationId());
    }

    /**
     * FLOW B.6: another operation or a webhook may have saved meanwhile: always apply on the latest copy.
     * RELOAD → APPLY → SAVE, a few times: the standard loop for optimistic concurrency.
     */
    private Payment record(Payment payment, String operationId, GatewayResult result, String actor) {
        for (int attempt = 1; ; attempt++) {
            Payment latest = payments.find(payment.id()).orElseThrow().actingAs(actor);
            GatewayResultApplier.applyOperation(latest, operationId, result);
            try {
                return payments.save(latest);
            } catch (ConcurrentUpdateException raced) {
                if (attempt == MAX_TRIES) {
                    throw raced;
                }
            }
        }
    }



    private OperationRequest request(Payment payment, PaymentOperation operation, String target) {
        return new OperationRequest(target, operation.amount(), operation.operationId(),
                merchants.credentials(payment.merchantId(), payment.providerId()).orElseThrow());
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
