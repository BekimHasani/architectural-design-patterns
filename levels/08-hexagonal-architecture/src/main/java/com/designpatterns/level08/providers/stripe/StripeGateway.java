package com.designpatterns.level08.providers.stripe;

import com.designpatterns.level08.payments.application.capabilities.ActionCompleter;
import com.designpatterns.level08.payments.application.capabilities.Authorizer;
import com.designpatterns.level08.payments.application.capabilities.Canceller;
import com.designpatterns.level08.payments.application.capabilities.Capability;
import com.designpatterns.level08.payments.application.capabilities.Capturer;
import com.designpatterns.level08.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level08.payments.application.capabilities.ProviderDescriptor;
import com.designpatterns.level08.payments.application.capabilities.Refunder;
import com.designpatterns.level08.payments.application.ports.AuthorizeRequest;
import com.designpatterns.level08.payments.application.ports.GatewayResult;
import com.designpatterns.level08.payments.application.ports.OperationRequest;
import com.designpatterns.level08.payments.application.ports.ProviderCredentials;
import com.designpatterns.level08.payments.domain.ProviderId;
import com.designpatterns.level08.payments.domain.RefundTerms;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.Currency;
import java.util.Set;
import java.util.function.Consumer;

import static com.designpatterns.level08.providers.support.ProviderHttp.exchange;
import static com.designpatterns.level08.providers.support.ProviderHttp.guard;

/** ADAPTER for the Stripe-like API. Stateless: the merchant's key comes with every call. */
public final class StripeGateway implements ProviderAdapter, Authorizer, ActionCompleter, Capturer, Canceller, Refunder {

    private static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(ProviderId.of("stripe"),
            Set.of(Capability.AUTHORIZE, Capability.CAPTURE, Capability.CANCEL, Capability.REFUND, Capability.CUSTOMER_ACTION),
            Set.of(Currency.getInstance("EUR"), Currency.getInstance("USD"), Currency.getInstance("GBP"), Currency.getInstance("JPY")),
            RefundTerms.ANY, ProviderDescriptor.Confirmation.BOTH);

    private final RestClient stripe;

    public StripeGateway(RestClient.Builder restClient, String baseUrl) {
        this.stripe = restClient.baseUrl(baseUrl).build();
    }

    @Override
    public ProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public GatewayResult authorize(AuthorizeRequest request) {
        return guard(() -> StripeMapper.toChargeResult(exchange(stripe.post()
                .uri("/v1/payment_intents")
                .headers(auth(request.credentials(), request.attemptId()))
                .body(StripeMapper.toRequest(request)))));
    }

    @Override
    public GatewayResult completeAction(String paymentIntentId, ProviderCredentials credentials) {
        return guard(() -> StripeMapper.toChargeResult(exchange(stripe.post()
                .uri("/v1/payment_intents/{id}/confirm", paymentIntentId)
                .headers(auth(credentials, null)))));
    }

    @Override
    public GatewayResult capture(OperationRequest request) {
        return guard(() -> StripeMapper.toOperationResult(exchange(stripe.post()
                .uri("/v1/payment_intents/{id}/capture", request.providerRef())
                .headers(auth(request.credentials(), request.operationId()))
                .body(new StripeApi.Capture(request.amount().amountMinor()))), request.providerRef()));
    }

    @Override
    public GatewayResult cancel(OperationRequest request) {
        return guard(() -> StripeMapper.toOperationResult(exchange(stripe.post()
                .uri("/v1/payment_intents/{id}/cancel", request.providerRef())
                .headers(auth(request.credentials(), request.operationId()))), request.providerRef()));
    }

    @Override
    public GatewayResult refund(OperationRequest request) {
        return guard(() -> StripeMapper.toOperationResult(exchange(stripe.post()
                .uri("/v1/refunds")
                .headers(auth(request.credentials(), request.operationId()))
                .body(new StripeApi.Refund(request.providerRef(), request.amount().amountMinor()))), request.providerRef()));
    }

    @Override
    public GatewayResult getStatus(String attemptId, ProviderCredentials credentials) {
        return guard(() -> StripeMapper.toStatusResult(exchange(stripe.get()
                .uri(b -> b.path("/v1/payment_intents/search")
                        .queryParam("query", "{query}")
                        .build("metadata['attempt_id']:'" + attemptId + "'"))
                .headers(auth(credentials, null)))));
    }

    private static Consumer<HttpHeaders> auth(ProviderCredentials credentials, String idempotencyKey) {
        return headers -> {
            headers.setBearerAuth(credentials.require("api-key"));
            if (idempotencyKey != null) {
                headers.set("Idempotency-Key", idempotencyKey);
            }
        };
    }
}
