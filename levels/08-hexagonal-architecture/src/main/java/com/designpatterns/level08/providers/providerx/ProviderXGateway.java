package com.designpatterns.level08.providers.providerx;

import com.designpatterns.level08.payments.application.capabilities.Capability;
import com.designpatterns.level08.payments.application.capabilities.DirectCharger;
import com.designpatterns.level08.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level08.payments.application.capabilities.ProviderDescriptor;
import com.designpatterns.level08.payments.application.capabilities.Refunder;
import com.designpatterns.level08.payments.application.ports.AuthorizeRequest;
import com.designpatterns.level08.payments.application.ports.GatewayResult;
import com.designpatterns.level08.payments.application.ports.OperationRequest;
import com.designpatterns.level08.payments.application.ports.ProviderCredentials;
import com.designpatterns.level08.payments.domain.ProviderId;
import com.designpatterns.level08.payments.domain.RefundTerms;
import org.springframework.web.client.RestClient;

import java.util.Currency;
import java.util.Set;

import static com.designpatterns.level08.providers.support.ProviderHttp.exchange;
import static com.designpatterns.level08.providers.support.ProviderHttp.guard;

/**
 * ADAPTER for Provider X, which charges directly. It implements DirectCharger and Refunder, and
 * nothing else: there is no capture or cancel method to answer "unsupported".
 */
public final class ProviderXGateway implements ProviderAdapter, DirectCharger, Refunder {

    private static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(ProviderId.of("providerx"),
            Set.of(Capability.CHARGE, Capability.REFUND),
            Set.of(Currency.getInstance("EUR"), Currency.getInstance("USD")),
            RefundTerms.ANY, ProviderDescriptor.Confirmation.SYNC);

    private final RestClient providerX;

    public ProviderXGateway(RestClient.Builder restClient, String baseUrl) {
        this.providerX = restClient.baseUrl(baseUrl).build();
    }

    @Override
    public ProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public GatewayResult charge(AuthorizeRequest r) {
        return guard(() -> ProviderXMapper.toChargeResult(exchange(providerX.post()
                .uri("/api/charge")
                .header("X-Merchant-Token", r.credentials().require("token"))
                .body(new ProviderXApi.Charge(r.amount().amountMinor(), r.amount().currency().getCurrencyCode(), r.attemptId())))));
    }

    @Override
    public GatewayResult refund(OperationRequest r) {
        return guard(() -> ProviderXMapper.toRefundResult(exchange(providerX.post()
                .uri("/api/charge/{id}/refund", r.providerRef())
                .header("X-Merchant-Token", r.credentials().require("token"))
                .body(new ProviderXApi.Refund(r.amount().amountMinor(), r.operationId()))), r.providerRef()));
    }

    @Override
    public GatewayResult getStatus(String attemptId, ProviderCredentials credentials) {
        return guard(() -> ProviderXMapper.toStatusResult(exchange(providerX.get()
                .uri(b -> b.path("/api/charge").queryParam("ref", attemptId).build())
                .header("X-Merchant-Token", credentials.require("token")))));
    }
}
