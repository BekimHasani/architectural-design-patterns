package com.designpatterns.level08.providers.asyncpay;

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
import com.designpatterns.level08.providers.support.SyntheticClient;
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
