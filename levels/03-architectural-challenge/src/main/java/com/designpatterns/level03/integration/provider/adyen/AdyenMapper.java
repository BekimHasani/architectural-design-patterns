package com.designpatterns.level03.integration.provider.adyen;

import com.designpatterns.level03.integration.gateway.ChargeRequest;
import com.designpatterns.level03.integration.gateway.GatewayResult;
import com.designpatterns.level03.integration.gateway.RefundRequest;
import com.designpatterns.level03.integration.support.ProviderHttp;
import com.designpatterns.level03.integration.support.ProviderHttp.Reply;

/** Adyen ⇄ our model. */
final class AdyenMapper {

    private AdyenMapper() {
    }

    static AdyenApi.PaymentRequest toRequest(ChargeRequest request, String merchantAccount) {
        return new AdyenApi.PaymentRequest(amount(request.amount().amountMinor(), request.amount().currency().getCurrencyCode()),
                request.attemptId(), merchantAccount, false);
    }

    static AdyenApi.ModificationRequest toRefund(RefundRequest request, String merchantAccount) {
        return new AdyenApi.ModificationRequest(amount(request.amount().amountMinor(), request.amount().currency().getCurrencyCode()),
                merchantAccount, request.operationId());
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

    static GatewayResult toRefundResult(Reply reply) {
        if (reply.status() / 100 != 2) {
            return ProviderHttp.failure(reply);
        }
        return ProviderHttp.parse(reply.body(), AdyenApi.ModificationResponse.class)
                .<GatewayResult>map(response -> new GatewayResult.Succeeded(response.pspReference()))
                .orElseGet(() -> ProviderHttp.unreadable(reply));
    }

    private static GatewayResult fromResponse(AdyenApi.PaymentResponse response) {
        return switch (response.resultCode()) {
            case "Authorised" -> new GatewayResult.Succeeded(response.pspReference());
            case "Refused" -> new GatewayResult.Declined(response.refusalReason());
            default -> new GatewayResult.Failed(GatewayResult.FailureKind.OUTCOME_UNKNOWN,
                    "Unexpected Adyen resultCode: " + response.resultCode());
        };
    }

    private static AdyenApi.Amount amount(long value, String currency) {
        return new AdyenApi.Amount(value, currency);
    }
}
