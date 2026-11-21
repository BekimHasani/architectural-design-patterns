package com.designpatterns.level08.providers.paypal;

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

/**
 * ADAPTER for the PayPal-like API.
 * authorize = create order (the buyer must approve); completeAction = read the order
 * (APPROVED means authorized); capture and void act on the order; refund acts on the CAPTURE.
 */
public final class PayPalGateway implements ProviderAdapter, Authorizer, ActionCompleter, Capturer, Canceller, Refunder {

    private static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(ProviderId.of("paypal"),
            Set.of(Capability.AUTHORIZE, Capability.CAPTURE, Capability.CANCEL, Capability.REFUND, Capability.CUSTOMER_ACTION),
            Set.of(Currency.getInstance("EUR"), Currency.getInstance("USD"), Currency.getInstance("GBP")),
            RefundTerms.ANY, ProviderDescriptor.Confirmation.SYNC);

    private final RestClient paypal;
    private final PayPalAccessTokens tokens;

    public PayPalGateway(RestClient.Builder restClient, String baseUrl) {
        this.paypal = restClient.baseUrl(baseUrl).build();
        this.tokens = new PayPalAccessTokens(paypal);
    }

    @Override
    public ProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public GatewayResult authorize(AuthorizeRequest request) {
        return guard(() -> PayPalMapper.toChargeResult(exchange(paypal.post()
                .uri("/v2/checkout/orders")
                .headers(auth(request.credentials(), request.attemptId()))
                .body(PayPalMapper.toCreateOrder(request)))));
    }

    @Override
    public GatewayResult completeAction(String orderId, ProviderCredentials credentials) {
        return guard(() -> PayPalMapper.toChargeResult(exchange(paypal.get()
                .uri("/v2/checkout/orders/{id}", orderId)
                .headers(auth(credentials, null)))));
    }

    @Override
    public GatewayResult capture(OperationRequest r) {
        return guard(() -> PayPalMapper.toCaptureResult(exchange(paypal.post()
                .uri("/v2/checkout/orders/{id}/capture", r.providerRef())
                .headers(auth(r.credentials(), r.operationId()))
                .body(new PayPalApi.CaptureOrder(PayPalMapper.toAmount(r.amount())))), r.providerRef()));
    }

    @Override
    public GatewayResult cancel(OperationRequest r) {
        return guard(() -> PayPalMapper.toOperationResult(exchange(paypal.post()
                .uri("/v2/checkout/orders/{id}/void", r.providerRef())
                .headers(auth(r.credentials(), r.operationId()))), r.providerRef()));
    }

    @Override
    public GatewayResult refund(OperationRequest r) {
        return guard(() -> PayPalMapper.toOperationResult(exchange(paypal.post()
                .uri("/v2/payments/captures/{id}/refund", r.providerRef())
                .headers(auth(r.credentials(), r.operationId()))
                .body(new PayPalApi.Refund(PayPalMapper.toAmount(r.amount())))), r.providerRef()));
    }

    @Override
    public GatewayResult getStatus(String attemptId, ProviderCredentials credentials) {
        return guard(() -> PayPalMapper.toStatusResult(exchange(paypal.get()
                .uri(b -> b.path("/v2/checkout/orders").queryParam("reference_id", attemptId).build())
                .headers(auth(credentials, null)))));
    }

    private Consumer<HttpHeaders> auth(ProviderCredentials credentials, String requestId) {
        return headers -> {
            headers.setBearerAuth(tokens.current(credentials));
            if (requestId != null) {
                headers.set("PayPal-Request-Id", requestId); // PayPal's name for an idempotency key
            }
        };
    }
}
