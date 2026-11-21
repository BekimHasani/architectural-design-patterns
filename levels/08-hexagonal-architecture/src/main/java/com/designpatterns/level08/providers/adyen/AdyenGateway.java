package com.designpatterns.level08.providers.adyen;

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

/** ADAPTER for the Adyen-like API. Stateless: API key and merchant account come with every call. */
public final class AdyenGateway implements ProviderAdapter, Authorizer, ActionCompleter, Capturer, Canceller, Refunder {

    private static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(ProviderId.of("adyen"),
            Set.of(Capability.AUTHORIZE, Capability.CAPTURE, Capability.CANCEL, Capability.REFUND, Capability.CUSTOMER_ACTION),
            Set.of(Currency.getInstance("EUR"), Currency.getInstance("USD"), Currency.getInstance("GBP"), Currency.getInstance("JPY")),
            RefundTerms.ANY, ProviderDescriptor.Confirmation.BOTH);

    private final RestClient adyen;

    public AdyenGateway(RestClient.Builder restClient, String baseUrl) {
        this.adyen = restClient.baseUrl(baseUrl).build();
    }

    @Override
    public ProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public GatewayResult authorize(AuthorizeRequest request) {
        return guard(() -> AdyenMapper.toChargeResult(exchange(adyen.post()
                .uri("/v71/payments")
                .headers(auth(request.credentials(), request.attemptId()))
                .body(AdyenMapper.toRequest(request)))));
    }

    @Override
    public GatewayResult completeAction(String pspReference, ProviderCredentials credentials) {
        return guard(() -> AdyenMapper.toChargeResult(exchange(adyen.post()
                .uri("/v71/payments/details")
                .headers(auth(credentials, null))
                .body(new AdyenApi.DetailsRequest(pspReference)))));
    }

    @Override
    public GatewayResult capture(OperationRequest request) {
        return modification(request, "captures");
    }

    @Override
    public GatewayResult cancel(OperationRequest request) {
        return modification(request, "cancels");
    }

    @Override
    public GatewayResult refund(OperationRequest request) {
        return modification(request, "refunds");
    }

    private GatewayResult modification(OperationRequest request, String action) {
        return guard(() -> AdyenMapper.toOperationResult(exchange(adyen.post()
                .uri("/v71/payments/{psp}/{action}", request.providerRef(), action)
                .headers(auth(request.credentials(), request.operationId()))
                .body(AdyenMapper.toModification(request))), request.providerRef()));
    }

    @Override
    public GatewayResult getStatus(String attemptId, ProviderCredentials credentials) {
        return guard(() -> AdyenMapper.toStatusResult(exchange(adyen.get()
                .uri(b -> b.path("/v71/payments").queryParam("merchantReference", attemptId).build())
                .headers(auth(credentials, null)))));
    }

    private static Consumer<HttpHeaders> auth(ProviderCredentials credentials, String idempotencyKey) {
        return headers -> {
            headers.set("X-API-Key", credentials.require("api-key"));
            if (idempotencyKey != null) {
                headers.set("Idempotency-Key", idempotencyKey);
            }
        };
    }
}
