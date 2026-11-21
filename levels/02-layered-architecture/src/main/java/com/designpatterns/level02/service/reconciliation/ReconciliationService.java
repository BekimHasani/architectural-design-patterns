package com.designpatterns.level02.service.reconciliation;

import org.springframework.stereotype.Service;

import com.designpatterns.level02.integration.gateway.GatewayResult;
import com.designpatterns.level02.integration.gateway.ProviderAdapter;
import com.designpatterns.level02.integration.gateway.ProviderCredentials;
import com.designpatterns.level02.integration.gateway.ProviderGateway;
import com.designpatterns.level02.model.CaptureMode;
import com.designpatterns.level02.model.Payment;
import com.designpatterns.level02.model.PaymentOperation;
import com.designpatterns.level02.model.PaymentStatus;
import com.designpatterns.level02.model.exceptions.ConcurrentUpdateException;
import com.designpatterns.level02.persistence.PaymentRepository;
import com.designpatterns.level02.service.merchant.MerchantService;
import com.designpatterns.level02.service.payment.GatewayResultApplier;
import com.designpatterns.level02.service.payment.PaymentOperationService;

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
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private static final String ACTOR = "reconciliation";

    private final PaymentRepository payments;
    private final ProviderGateway gateways;
    private final MerchantService merchants;
    private final PaymentOperationService operations;

    public ReconciliationService(PaymentRepository payments, ProviderGateway gateways,
                                    MerchantService merchants, PaymentOperationService operations) {
        this.payments = Objects.requireNonNull(payments, "payments");
        this.gateways = Objects.requireNonNull(gateways, "gateways");
        this.merchants = Objects.requireNonNull(merchants, "merchants");
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    public record Report(int checked, int resolved) {
    }

    public Report run() {
        int checked = 0;
        int resolved = 0;
        // FLOW D.2: authorizations we don't know the end of → ASK the provider (never re-send)
        List<Payment> waiting = new ArrayList<>(payments.findByStatus(PaymentStatus.UNKNOWN));
        waiting.addAll(payments.findByStatus(PaymentStatus.PROCESSING));
        for (Payment payment : waiting) {
            checked++;
            resolved += reconcileAuthorization(payment) ? 1 : 0;
        }
        // FLOW D.4: captures / cancels / refunds with an unknown outcome → RE-SEND with the same operation
        // id; the provider's idempotency turns it into "tell me the answer again"
        for (Payment payment : payments.findWithUnknownOperations()) {
            for (PaymentOperation operation : payment.operations()) {
                if (operation.status() == PaymentOperation.Status.UNKNOWN) {
                    checked++;
                    resolved += operations.resend(payment, operation) ? 1 : 0;
                }
            }
        }
        // FLOW D.5: automatic-capture payments whose authorization arrived late (webhook): capture now
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
        ProviderCredentials creds = merchants.credentials(payment.merchantId(), payment.providerId()).orElseThrow();
        // "what happened to attempt X?": every provider must answer this (it is on ProviderAdapter itself)
        GatewayResult status = gateways.call(payment.providerId(), ProviderAdapter.class,
                adapter -> adapter.getStatus(payment.attemptId(), creds));
        // FLOW D.3: still unknown → leave it for the next run; otherwise apply and save
        if (!GatewayResultApplier.applyStatus(payment, status)) {
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

}
