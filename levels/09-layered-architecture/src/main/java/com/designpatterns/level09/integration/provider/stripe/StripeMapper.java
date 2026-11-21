package com.designpatterns.level09.integration.provider.stripe;

import com.designpatterns.level09.integration.gateway.AuthorizeRequest;
import com.designpatterns.level09.integration.gateway.FailureCategory;
import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.integration.support.ProviderHttp;
import com.designpatterns.level09.model.DeclineReason;

import com.designpatterns.level09.integration.support.ProviderHttp.Reply;

import java.net.URI;
import java.util.Map;

/**
 * Stripe ⇄ our model (MAPPER, part of the ANTI-CORRUPTION LAYER). Pure functions: tested with recorded
 * JSON, no network. Stripe's words ("requires_capture", "card_error") never leave this class.
 */
final class StripeMapper {

    private StripeMapper() {
    }

    static StripeApi.CreatePaymentIntent toRequest(AuthorizeRequest request) {
        return new StripeApi.CreatePaymentIntent(
                request.amount().amountMinor(), // Stripe uses minor units, like us
                request.amount().currency().getCurrencyCode().toLowerCase(),
                true,
                "manual",
                Map.of("attempt_id", request.attemptId()));
    }

    /** Search by our attempt id: no hit means Stripe never processed the attempt. */
    static GatewayResult toStatusResult(Reply reply) {
        if (reply.status() != 200) {
            return ProviderHttp.failure(reply);
        }
        return ProviderHttp.parse(reply.body(), StripeApi.SearchResult.class)
                .map(found -> found.data() == null || found.data().isEmpty()
                        ? ProviderHttp.notProcessed()
                        : fromIntent(found.data().get(0)))
                .orElseGet(() -> ProviderHttp.unreadable(reply));
    }

    static GatewayResult toChargeResult(Reply reply) {
        return switch (reply.status()) {
            case 200 -> ProviderHttp.parse(reply.body(), StripeApi.PaymentIntent.class)
                    .map(StripeMapper::fromIntent)
                    .orElseGet(() -> ProviderHttp.unreadable(reply));
            case 402 -> ProviderHttp.parse(reply.body(), StripeApi.ErrorResponse.class)
                    .filter(e -> "card_error".equals(e.error().type()))
                    .<GatewayResult>map(e -> new GatewayResult.Declined(declineReason(e.error().declineCode())))
                    .orElseGet(() -> ProviderHttp.failure(reply));
            default -> ProviderHttp.failure(reply);
        };
    }

    /** Capture, cancel, refund: a 2xx means done. */
    static GatewayResult toOperationResult(Reply reply, String providerRef) {
        return reply.status() / 100 == 2 ? new GatewayResult.Done(providerRef) : ProviderHttp.failure(reply);
    }

    private static GatewayResult fromIntent(StripeApi.PaymentIntent intent) {
        return switch (intent.status()) {
            case "requires_capture" -> new GatewayResult.Authorized(intent.id());
            case "succeeded" -> new GatewayResult.Charged(intent.id());
            case "requires_action" -> new GatewayResult.RequiresAction(intent.id(),
                    URI.create(intent.nextAction().redirectToUrl().url()));
            case "processing" -> new GatewayResult.Processing(intent.id()); // final answer comes by webhook
            default -> new GatewayResult.Failed(FailureCategory.OUTCOME_UNKNOWN, "Unexpected Stripe status: " + intent.status());
        };
    }

    private static DeclineReason declineReason(String declineCode) {
        return switch (declineCode == null ? "" : declineCode) {
            case "insufficient_funds" -> DeclineReason.INSUFFICIENT_FUNDS;
            case "fraudulent", "stolen_card", "lost_card" -> DeclineReason.SUSPECTED_FRAUD;
            default -> DeclineReason.GENERIC;
        };
    }
}
