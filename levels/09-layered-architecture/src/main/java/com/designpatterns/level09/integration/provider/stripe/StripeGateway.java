package com.designpatterns.level09.integration.provider.stripe;

import com.designpatterns.level09.integration.gateway.AuthorizeRequest;
import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.integration.gateway.OperationRequest;
import com.designpatterns.level09.integration.gateway.ProviderAdapter;
import com.designpatterns.level09.integration.gateway.ProviderCredentials;
import com.designpatterns.level09.integration.gateway.ProviderDescriptor;
import com.designpatterns.level09.integration.gateway.capability.ActionCompleter;
import com.designpatterns.level09.integration.gateway.capability.Authorizer;
import com.designpatterns.level09.integration.gateway.capability.Canceller;
import com.designpatterns.level09.integration.gateway.capability.Capturer;
import com.designpatterns.level09.integration.gateway.capability.Refunder;
import com.designpatterns.level09.model.Capability;
import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.model.RefundTerms;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.Currency;
import java.util.Set;
import java.util.function.Consumer;

import static com.designpatterns.level09.integration.support.ProviderHttp.exchange;
import static com.designpatterns.level09.integration.support.ProviderHttp.guard;

/**
 * ADAPTER for the Stripe-like API. Stateless: the merchant's key comes with every call.
 *
 * <p>The {@code implements} list IS the list of capabilities (Interface Segregation). The DESCRIPTOR says
 * the same in data, for routing; {@code DefaultProviderGateway} checks at startup that the two agree.
 *
 * <p>Every method has the same shape (FLOW A.10 / B.5 / D.2):
 * <pre>
 *   guard( () -&gt; Mapper.toResult( exchange( http request built from Mapper.toRequest(...) ) ) )
 * </pre>
 * {@code exchange} sends and returns status + body; the MAPPER turns that into a {@code GatewayResult};
 * {@code guard} turns network exceptions into {@code Failed} results (timeouts → OUTCOME_UNKNOWN).
 * The adapter never throws for a provider problem: a failure is a normal, typed answer.
 */
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
                // OUR attempt id as the provider's Idempotency-Key: if a retry re-sends this request,
                // Stripe returns the first answer instead of creating a second charge
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
