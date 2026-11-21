package com.designpatterns.level02.service.payment;

import org.springframework.stereotype.Service;

import com.designpatterns.level02.model.Capability;
import com.designpatterns.level02.model.CaptureMode;
import com.designpatterns.level02.model.MerchantId;
import com.designpatterns.level02.model.Money;
import com.designpatterns.level02.model.Payment;
import com.designpatterns.level02.model.PaymentId;
import com.designpatterns.level02.model.PaymentStatus;
import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.persistence.PaymentAttemptRepository;
import com.designpatterns.level02.persistence.PaymentRepository;
import com.designpatterns.level02.service.exception.NoProviderAvailableException;
import com.designpatterns.level02.service.exception.UnknownMerchantException;
import com.designpatterns.level02.service.exception.UnknownPaymentException;
import com.designpatterns.level02.service.merchant.MerchantService;
import com.designpatterns.level02.service.routing.ProviderRoutingService;
import com.designpatterns.level02.service.routing.RoutingContext;


import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Commands that bring a payment to AUTHORIZED: start it, and finish it after a customer action.
 * Only providers this merchant has credentials for are candidates.
 *
 * <p>FLOW A.5 – A.8 happen here, then the {@link AuthorizationOrchestrator} talks to providers (A.9 – A.13).
 *
 * <p>{@code @Service}: Spring creates one instance and passes the constructor arguments. Its dependencies
 * are interfaces or other services, so a unit test can build it with fakes ({@code PaymentServiceTest}).
 * The {@code Objects.requireNonNull} calls make a mis-wired object fail at construction, not later.
 */
@Service
public class PaymentService {

    private final ProviderRoutingService router;
    private final AuthorizationOrchestrator orchestrator;
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final MerchantService merchants;
    private final PaymentOperationService operations;

    public PaymentService(ProviderRoutingService router, AuthorizationOrchestrator orchestrator, PaymentRepository payments,
                            PaymentAttemptRepository attempts, MerchantService merchants, PaymentOperationService operations) {
        this.router = Objects.requireNonNull(router, "router");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.merchants = Objects.requireNonNull(merchants, "merchants");
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
        // FLOW A.5: is this a merchant we know? (→ 403 via ApiExceptionHandler)
        if (!merchants.knows(merchantId)) {
            throw new UnknownMerchantException(merchantId);
        }
        // FLOW A.6: ROUTING. Which providers could take this currency / mode / capabilities, in which order?
        List<ProviderId> candidates = router
                .candidates(new RoutingContext(amount.currency(), Optional.of(merchantId.value()), mode, alsoRequired))
                .stream()
                // FLOW A.7: ...and of those, which ones has THIS merchant signed up with (has credentials for)?
                .filter(provider -> merchants.credentials(merchantId, provider).isPresent())
                .toList();
        if (candidates.isEmpty()) {
            throw new NoProviderAvailableException(amount.currency(), mode, alsoRequired); // → 422
        }
        // FLOW A.8: create the aggregate (it checks its own rules) and store it BEFORE talking to anyone.
        // Payment.create is a STATIC FACTORY METHOD: it records the "PaymentCreated" event too.
        Payment payment = payments.save(Payment.create(PaymentId.newId(), merchantId, customerId, amount, mode));
        // FLOW A.9 – A.13 in the orchestrator, then A.14 in finish()
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

    /**
     * FLOW A.14: AUTOMATIC on a provider that holds funds first: the capture follows at once (that is
     * FLOW B, started by us instead of by the merchant).
     */
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
