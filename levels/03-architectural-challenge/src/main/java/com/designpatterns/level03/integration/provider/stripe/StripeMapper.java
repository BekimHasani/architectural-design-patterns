package com.designpatterns.level03.integration.provider.stripe;

import com.designpatterns.level03.integration.gateway.ChargeRequest;
import com.designpatterns.level03.integration.gateway.GatewayResult;
import com.designpatterns.level03.integration.support.ProviderHttp;
import com.designpatterns.level03.integration.support.ProviderHttp.Reply;

import java.util.Map;

/** Stripe ⇄ our model. Stripe's words ("succeeded", "card_error") never leave this class. */
final class StripeMapper {

    private StripeMapper() {
    }

    static StripeApi.CreatePaymentIntent toRequest(ChargeRequest request) {
        return new StripeApi.CreatePaymentIntent(
                request.amount().amountMinor(),
                request.amount().currency().getCurrencyCode().toLowerCase(),
                true,
                "automatic",
                Map.of("attempt_id", request.attemptId()));
    }

    static GatewayResult toChargeResult(Reply reply) {
        return switch (reply.status()) {
            case 200 -> ProviderHttp.parse(reply.body(), StripeApi.PaymentIntent.class)
                    .map(StripeMapper::fromIntent)
                    .orElseGet(() -> ProviderHttp.unreadable(reply));
            case 402 -> ProviderHttp.parse(reply.body(), StripeApi.ErrorResponse.class)
                    .filter(e -> "card_error".equals(e.error().type()))
                    .<GatewayResult>map(e -> new GatewayResult.Declined(e.error().declineCode()))
                    .orElseGet(() -> ProviderHttp.failure(reply));
            default -> ProviderHttp.failure(reply);
        };
    }

    static GatewayResult toRefundResult(Reply reply) {
        if (reply.status() != 200) {
            return ProviderHttp.failure(reply);
        }
        return ProviderHttp.parse(reply.body(), StripeApi.RefundResponse.class)
                .<GatewayResult>map(refund -> new GatewayResult.Succeeded(refund.id()))
                .orElseGet(() -> ProviderHttp.unreadable(reply));
    }

    private static GatewayResult fromIntent(StripeApi.PaymentIntent intent) {
        return "succeeded".equals(intent.status())
                ? new GatewayResult.Succeeded(intent.id())
                : new GatewayResult.Failed(GatewayResult.FailureKind.OUTCOME_UNKNOWN, "Unexpected Stripe status: " + intent.status());
    }
}
