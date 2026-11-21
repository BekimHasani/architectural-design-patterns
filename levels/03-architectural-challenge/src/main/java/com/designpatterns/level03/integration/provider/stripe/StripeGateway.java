package com.designpatterns.level03.integration.provider.stripe;

import com.designpatterns.level03.integration.gateway.ChargeRequest;
import com.designpatterns.level03.integration.gateway.GatewayResult;
import com.designpatterns.level03.integration.gateway.PaymentGateway;
import com.designpatterns.level03.integration.gateway.RefundRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.function.Consumer;

import static com.designpatterns.level03.integration.support.ProviderHttp.exchange;
import static com.designpatterns.level03.integration.support.ProviderHttp.guard;

/** ADAPTER for the Stripe-like API. */
@Component
public class StripeGateway implements PaymentGateway {

    private final RestClient stripe;
    private final String apiKey;

    public StripeGateway(@Value("${payment.stripe.base-url}") String baseUrl,
                         @Value("${payment.stripe.api-key}") String apiKey) {
        this.stripe = com.designpatterns.level03.integration.support.ProviderHttp.restClient(baseUrl);
        this.apiKey = apiKey;
    }

    @Override
    public String id() {
        return "stripe";
    }

    @Override
    public GatewayResult charge(ChargeRequest request) {
        return guard(() -> StripeMapper.toChargeResult(exchange(stripe.post()
                .uri("/v1/payment_intents")
                .headers(auth(request.attemptId()))
                .body(StripeMapper.toRequest(request)))));
    }

    @Override
    public GatewayResult refund(RefundRequest request) {
        return refund(request, null);
    }

    /** Stripe also accepts a refund reason, which its dashboard shows to the merchant. */
    public GatewayResult refund(RefundRequest request, String reason) {
        return guard(() -> StripeMapper.toRefundResult(exchange(stripe.post()
                .uri("/v1/refunds")
                .headers(auth(request.operationId()))
                .body(new StripeApi.Refund(request.providerReference(), request.amount().amountMinor(), reason)))));
    }

    private Consumer<HttpHeaders> auth(String idempotencyKey) {
        return headers -> {
            headers.setBearerAuth(apiKey);
            headers.set("Idempotency-Key", idempotencyKey);
        };
    }
}
