package com.designpatterns.level02.integration.provider.asyncpay;

import com.designpatterns.level02.integration.gateway.AuthorizeRequest;
import com.designpatterns.level02.integration.gateway.GatewayResult;
import com.designpatterns.level02.integration.gateway.OperationRequest;
import com.designpatterns.level02.integration.gateway.ProviderAdapter;
import com.designpatterns.level02.integration.gateway.ProviderCredentials;
import com.designpatterns.level02.integration.gateway.ProviderDescriptor;
import com.designpatterns.level02.integration.gateway.capability.DirectCharger;
import com.designpatterns.level02.integration.gateway.capability.Refunder;
import com.designpatterns.level02.integration.support.SyntheticClient;
import com.designpatterns.level02.model.Capability;
import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.model.RefundTerms;

import org.springframework.web.client.RestClient;

import java.util.Currency;
import java.util.Set;

/** Always answers later, takes only EUR and SEK, and refunds once, in full. */
public final class AsyncPayGateway implements ProviderAdapter, DirectCharger, Refunder {

    private static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(ProviderId.of("asyncpay"),
            Set.of(Capability.CHARGE, Capability.REFUND), Set.of(Currency.getInstance("EUR"), Currency.getInstance("SEK")),
            new RefundTerms(false, 1), ProviderDescriptor.Confirmation.ASYNC);

    private final SyntheticClient client;

    public AsyncPayGateway(RestClient.Builder http, String baseUrl) {
        this.client = new SyntheticClient(http, baseUrl);
    }

    @Override
    public ProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public GatewayResult charge(AuthorizeRequest request) {
        return client.pay(request);
    }

    @Override
    public GatewayResult refund(OperationRequest request) {
        return client.operate("refund", request);
    }

    @Override
    public GatewayResult getStatus(String attemptId, ProviderCredentials credentials) {
        return client.status(attemptId, credentials);
    }
}
