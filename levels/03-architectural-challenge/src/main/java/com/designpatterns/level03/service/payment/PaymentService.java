package com.designpatterns.level03.service.payment;

import com.designpatterns.level03.integration.gateway.ChargeRequest;
import com.designpatterns.level03.integration.gateway.GatewayResult;
import com.designpatterns.level03.integration.gateway.ProviderGateways;
import com.designpatterns.level03.integration.gateway.RefundRequest;
import com.designpatterns.level03.model.Money;
import com.designpatterns.level03.model.Payment;
import com.designpatterns.level03.model.exceptions.PaymentNotFoundException;
import com.designpatterns.level03.model.exceptions.PaymentRuleViolationException;
import com.designpatterns.level03.persistence.PaymentRepository;
import com.designpatterns.level03.service.routing.RoutingService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Charge, refund and look up payments. */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final ProviderGateways gateways;
    private final RoutingService routing;

    public PaymentService(PaymentRepository payments, ProviderGateways gateways, RoutingService routing) {
        this.payments = payments;
        this.gateways = gateways;
        this.routing = routing;
    }

    /**
     * Charges the customer at the first provider that can do it. The payment is stored BEFORE any provider
     * is called, so a crash mid-call still leaves a trace.
     */
    public Payment charge(ChargeCommand command) {
        Payment payment = Payment.create("pay_" + UUID.randomUUID(), command.merchantId(), command.customerId(), command.amount());
        payments.save(payment);

        String lastFailure = "No provider available for " + command.amount().currency();
        for (String providerId : routing.candidatesFor(command.amount().currency())) {
            String attemptId = "att_" + UUID.randomUUID();
            System.out.println("Charging " + command.amount() + " at " + providerId + " (payment " + payment.id() + ", attempt " + attemptId + ")");
            GatewayResult result = gateways.get(providerId).charge(new ChargeRequest(attemptId, command.amount()));

            if (result instanceof GatewayResult.Succeeded succeeded) {
                payment.markCaptured(providerId, succeeded.providerReference());
                return payments.save(payment);
            } else if (result instanceof GatewayResult.Declined declined) {
                payment.markDeclined(providerId, declined.reason());
                return payments.save(payment);
            } else if (result instanceof GatewayResult.Failed failed) {
                if (!failed.kind().equals(GatewayResult.FailureKind.NOTHING_CHARGED)) {
                    System.out.println("Provider " + providerId + " outcome unknown: " + failed.detail() + ", not trying another provider");
                    payment.markOutcomeUnknown(providerId, failed.detail());
                    return payments.save(payment);
                }

                System.out.println("Provider " + providerId + " charged nothing: " + failed.detail() + ", trying the next one");
                lastFailure = providerId + ": " + failed.detail();
            }
        }
        payment.markFailed(lastFailure);
        return payments.save(payment);
    }

    /** Refunds part or all of a captured payment, at the provider that charged it. */
    public Payment refund(String merchantId, String paymentId, Money amount, String reason) {
        Payment payment = payments.findById(paymentId)
                .filter(p -> p.merchantId().equals(merchantId))
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        payment.checkRefundable(amount);

        // The service talks to the PaymentGateway contract only. Whether a provider can show the reason
        // is the adapter's business, so a new provider never adds a branch here.
        RefundRequest request = new RefundRequest("ref_" + UUID.randomUUID(), payment.providerReference(), amount, reason);
        GatewayResult result = gateways.get(payment.providerId()).refund(request);

        if (result instanceof GatewayResult.Succeeded) {
            payment.recordRefund(amount);
            return payments.save(payment);
        }
        throw new PaymentRuleViolationException("Refund failed at " + payment.providerId() + ": " + result);
    }

    /** Everything the GET endpoint shows, ready to return. */
    public Payment find(String merchantId, String paymentId) {
        return payments.findById(paymentId)
                .filter(p -> p.merchantId().equals(merchantId))
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}