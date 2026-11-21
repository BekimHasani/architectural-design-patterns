package com.designpatterns.level01.payments.application.reconciliation;

import com.designpatterns.level01.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level01.payments.application.commands.PaymentOperations;
import com.designpatterns.level01.payments.application.ports.ConcurrentUpdateException;
import com.designpatterns.level01.payments.application.ports.FailureCategory;
import com.designpatterns.level01.payments.application.ports.GatewayRegistry;
import com.designpatterns.level01.payments.application.ports.GatewayResult;
import com.designpatterns.level01.payments.application.ports.MerchantCredentials;
import com.designpatterns.level01.payments.application.ports.PaymentRepository;
import com.designpatterns.level01.payments.application.ports.ProviderCredentials;
import com.designpatterns.level01.payments.domain.CaptureMode;
import com.designpatterns.level01.payments.domain.Payment;
import com.designpatterns.level01.payments.domain.PaymentOperation;
import com.designpatterns.level01.payments.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Use case: settle everything whose outcome we don't know.
 * <ul>
 *   <li>authorizations (UNKNOWN, PROCESSING): ASK the provider. Never re-send: if the first request
 *       never arrived, re-sending would start a charge the customer has stopped waiting for;</li>
 *   <li>captures, cancels, refunds: RE-SEND with the same operation id. They act on an existing
 *       payment and the provider replays its answer, so this is safe.</li>
 * </ul>
 */
public final class ReconcileUnknownPayments {

    private static final Logger log = LoggerFactory.getLogger(ReconcileUnknownPayments.class);
    private static final String ACTOR = "reconciliation";

    private final PaymentRepository payments;
    private final GatewayRegistry gateways;
    private final MerchantCredentials credentials;
    private final PaymentOperations operations;

    public ReconcileUnknownPayments(PaymentRepository payments, GatewayRegistry gateways,
                                    MerchantCredentials credentials, PaymentOperations operations) {
        this.payments = Objects.requireNonNull(payments, "payments");
        this.gateways = Objects.requireNonNull(gateways, "gateways");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    public record Report(int checked, int resolved) {
    }

    public Report run() {
        int checked = 0;
        int resolved = 0;
        List<Payment> waiting = new ArrayList<>(payments.findByStatus(PaymentStatus.UNKNOWN));
        waiting.addAll(payments.findByStatus(PaymentStatus.PROCESSING));
        for (Payment payment : waiting) {
            checked++;
            resolved += reconcileAuthorization(payment) ? 1 : 0;
        }
        for (Payment payment : payments.findWithUnknownOperations()) {
            for (PaymentOperation operation : payment.operations()) {
                if (operation.status() == PaymentOperation.Status.UNKNOWN) {
                    checked++;
                    resolved += operations.resend(payment, operation) ? 1 : 0;
                }
            }
        }
        for (Payment payment : payments.findByStatus(PaymentStatus.AUTHORIZED)) {
            // AUTOMATIC, but the authorization arrived later by webhook: the capture is still owed
            if (payment.captureMode() == CaptureMode.AUTOMATIC && payment.operations().isEmpty()) {
                checked++;
                operations.capture(payment.merchantId(), payment.id(), null);
                resolved++;
            }
        }
        return new Report(checked, resolved);
    }

    private boolean reconcileAuthorization(Payment payment) {
        payment.actingAs(ACTOR);
        ProviderCredentials creds = credentials.find(payment.merchantId(), payment.providerId()).orElseThrow();
        GatewayResult status = gateways.call(payment.providerId(), ProviderAdapter.class,
                adapter -> adapter.getStatus(payment.attemptId(), creds));
        if (!resolve(payment, status)) {
            return false;
        }
        try {
            payments.save(payment);
            log.info("Reconciled payment {} → {}", payment.id(), payment.status());
            return true;
        } catch (ConcurrentUpdateException raced) {
            return false; // a webhook got there first; the next run will see
        }
    }

    /** @return true if the provider's answer settles the payment */
    private static boolean resolve(Payment payment, GatewayResult status) {
        switch (status) {
            case GatewayResult.Authorized a -> payment.authorized(a.providerRef());
            case GatewayResult.Charged c -> payment.charged(c.providerRef());
            case GatewayResult.Declined d -> payment.declined(d.reason());
            case GatewayResult.RequiresAction a -> payment.requiresAction(a.providerRef(), a.redirectUrl());
            case GatewayResult.Processing p when payment.status() == PaymentStatus.PROCESSING -> {
                return false;
            }
            case GatewayResult.Processing p -> payment.processing(p.providerRef());
            case GatewayResult.Failed f when f.category() == FailureCategory.NOT_PROCESSED ->
                    payment.failed("Provider has no record of the attempt: nothing was charged");
            case GatewayResult.Failed f -> {
                return false;
            }
            case GatewayResult.Done d -> {
                return false;
            }
        }
        return true;
    }
}
