package com.designpatterns.level09.integration.provider.adyen;

import com.designpatterns.level09.integration.gateway.AuthorizeRequest;
import com.designpatterns.level09.integration.gateway.FailureCategory;
import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.integration.gateway.OperationRequest;
import com.designpatterns.level09.integration.support.ProviderHttp;
import com.designpatterns.level09.model.DeclineReason;

import com.designpatterns.level09.integration.support.ProviderHttp.Reply;

import java.net.URI;

/** Adyen ⇄ our model. Pure functions: tested with recorded JSON, no network. */
final class AdyenMapper {

    private AdyenMapper() {
    }

    static AdyenApi.PaymentRequest toRequest(AuthorizeRequest request) {
        return new AdyenApi.PaymentRequest(
                new AdyenApi.Amount(request.amount().amountMinor(), request.amount().currency().getCurrencyCode()),
                request.attemptId(), // merchantReference: lets us find the payment after a lost response
                request.credentials().require("merchant-account"),
                true);
    }

    static AdyenApi.ModificationRequest toModification(OperationRequest request) {
        AdyenApi.Amount amount = request.amount() == null ? null
                : new AdyenApi.Amount(request.amount().amountMinor(), request.amount().currency().getCurrencyCode());
        return new AdyenApi.ModificationRequest(amount, request.credentials().require("merchant-account"), request.operationId());
    }

    /** Refunds point at the ORIGINAL pspReference, so that one is kept. */
    static GatewayResult toOperationResult(Reply reply, String pspReference) {
        return reply.status() / 100 == 2 ? new GatewayResult.Done(pspReference) : ProviderHttp.failure(reply);
    }

    static GatewayResult toChargeResult(Reply reply) {
        if (reply.status() != 200) {
            return ProviderHttp.failure(reply);
        }
        // HTTP 200 says nothing about the outcome. Read resultCode.
        return ProviderHttp.parse(reply.body(), AdyenApi.PaymentResponse.class)
                .map(AdyenMapper::fromResponse)
                .orElseGet(() -> ProviderHttp.unreadable(reply));
    }

    /** Lookup by our reference: no payment means Adyen never processed the attempt. */
    static GatewayResult toStatusResult(Reply reply) {
        if (reply.status() != 200) {
            return ProviderHttp.failure(reply);
        }
        return ProviderHttp.parse(reply.body(), AdyenApi.PaymentList.class)
                .map(found -> found.payments() == null || found.payments().isEmpty()
                        ? ProviderHttp.notProcessed()
                        : fromResponse(found.payments().get(0)))
                .orElseGet(() -> ProviderHttp.unreadable(reply));
    }

    private static GatewayResult fromResponse(AdyenApi.PaymentResponse response) {
        return switch (response.resultCode()) {
            case "Authorised" -> new GatewayResult.Authorized(response.pspReference());
            case "Refused" -> new GatewayResult.Declined(declineReason(response.refusalReason()));
            case "RedirectShopper" -> new GatewayResult.RequiresAction(response.pspReference(),
                    URI.create(response.action().url()));
            case "Received", "Pending" -> new GatewayResult.Processing(response.pspReference()); // final answer by notification
            default -> new GatewayResult.Failed(FailureCategory.OUTCOME_UNKNOWN, "Unexpected Adyen resultCode: " + response.resultCode());
        };
    }

    private static DeclineReason declineReason(String refusalReason) {
        return switch (refusalReason == null ? "" : refusalReason) {
            case "Not enough balance" -> DeclineReason.INSUFFICIENT_FUNDS;
            case "FRAUD", "FRAUD-CANCELLED" -> DeclineReason.SUSPECTED_FRAUD;
            default -> DeclineReason.GENERIC;
        };
    }
}
