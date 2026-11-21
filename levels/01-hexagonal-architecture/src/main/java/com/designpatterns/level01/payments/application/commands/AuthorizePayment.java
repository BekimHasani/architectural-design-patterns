package com.designpatterns.level01.payments.application.commands;

import com.designpatterns.level01.payments.application.capabilities.Capability;
import com.designpatterns.level01.payments.application.orchestration.PaymentOrchestrator;
import com.designpatterns.level01.payments.application.ports.MerchantCredentials;
import com.designpatterns.level01.payments.application.ports.PaymentAttemptLog;
import com.designpatterns.level01.payments.application.ports.PaymentRepository;
import com.designpatterns.level01.payments.application.routing.ProviderRouter;
import com.designpatterns.level01.payments.application.routing.RoutingContext;
import com.designpatterns.level01.payments.domain.CaptureMode;
import com.designpatterns.level01.payments.domain.MerchantId;
import com.designpatterns.level01.payments.domain.Money;
import com.designpatterns.level01.payments.domain.Payment;
import com.designpatterns.level01.payments.domain.PaymentId;
import com.designpatterns.level01.payments.domain.PaymentStatus;
import com.designpatterns.level01.payments.domain.ProviderId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Commands that bring a payment to AUTHORIZED: start it, and finish it after a customer action.
 * Only providers this merchant has credentials for are candidates.
 */
public final class AuthorizePayment {

    private final ProviderRouter router;
    private final PaymentOrchestrator orchestrator;
    private final PaymentRepository payments;
    private final PaymentAttemptLog attempts;
    private final MerchantCredentials credentials;
    private final PaymentOperations operations;

    public AuthorizePayment(ProviderRouter router, PaymentOrchestrator orchestrator, PaymentRepository payments,
                            PaymentAttemptLog attempts, MerchantCredentials credentials, PaymentOperations operations) {
        this.router = Objects.requireNonNull(router, "router");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    public PaymentResult authorize(MerchantId merchantId, String customerId, Money amount) {
        return authorize(merchantId, customerId, amount, CaptureMode.MANUAL, Set.of());
    }

    /**
     * @param mode         MANUAL: hold the funds. AUTOMATIC: take the money now, in whatever way the
     *                     chosen provider does it.
     * @param alsoRequired capabilities the merchant insists on, e.g. REFUND. Providers without them
     *                     aren't even considered.
     */
    public PaymentResult authorize(MerchantId merchantId, String customerId, Money amount, CaptureMode mode,
                                   Set<Capability> alsoRequired) {
        if (!credentials.knows(merchantId)) {
            throw new UnknownMerchantException(merchantId);
        }
        List<ProviderId> candidates = router
                .candidates(new RoutingContext(amount.currency(), Optional.of(merchantId.value()), mode, alsoRequired))
                .stream()
                .filter(provider -> credentials.find(merchantId, provider).isPresent())
                .toList();
        if (candidates.isEmpty()) {
            throw new NoProviderAvailableException(amount.currency(), mode, alsoRequired);
        }
        Payment payment = payments.save(Payment.create(PaymentId.newId(), merchantId, customerId, amount, mode));
        return finish(orchestrator.authorize(payment, candidates));
    }

    /** Idempotent by state: if the payment no longer waits for the customer, nothing is called. */
    public PaymentResult complete(MerchantId merchantId, PaymentId paymentId) {
        Payment payment = load(merchantId, paymentId);
        if (payment.status() == PaymentStatus.ACTION_REQUIRED) {
            return finish(orchestrator.complete(payment));
        }
        return new PaymentResult(payment, attempts.forPayment(paymentId));
    }

    /** AUTOMATIC on a provider that holds funds first: the capture follows at once. */
    private PaymentResult finish(Payment payment) {
        if (payment.captureMode() == CaptureMode.AUTOMATIC && payment.status() == PaymentStatus.AUTHORIZED) {
            return operations.capture(payment.merchantId(), payment.id(), null);
        }
        return new PaymentResult(payment, attempts.forPayment(payment.id()));
    }

    public PaymentResult current(MerchantId merchantId, PaymentId paymentId) {
        return new PaymentResult(load(merchantId, paymentId), attempts.forPayment(paymentId));
    }

    private Payment load(MerchantId merchantId, PaymentId paymentId) {
        return payments.find(paymentId)
                .filter(p -> p.merchantId().equals(merchantId))
                .orElseThrow(() -> new UnknownPaymentException(paymentId));
    }
}
