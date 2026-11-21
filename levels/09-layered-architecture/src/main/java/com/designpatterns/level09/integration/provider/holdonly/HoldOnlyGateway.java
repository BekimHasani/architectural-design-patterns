package com.designpatterns.level09.integration.provider.holdonly;

import com.designpatterns.level09.integration.gateway.AuthorizeRequest;
import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.integration.gateway.OperationRequest;
import com.designpatterns.level09.integration.gateway.ProviderAdapter;
import com.designpatterns.level09.integration.gateway.ProviderCredentials;
import com.designpatterns.level09.integration.gateway.ProviderDescriptor;
import com.designpatterns.level09.integration.gateway.capability.Authorizer;
import com.designpatterns.level09.integration.gateway.capability.Canceller;
import com.designpatterns.level09.integration.gateway.capability.Capturer;
import com.designpatterns.level09.integration.support.SyntheticClient;
import com.designpatterns.level09.model.Capability;
import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.model.RefundTerms;

import org.springframework.web.client.RestClient;

import java.util.Currency;
import java.util.Set;

/** Holds and captures, but can never give money back: there is no refund method to call. */
public final class HoldOnlyGateway implements ProviderAdapter, Authorizer, Capturer, Canceller {

    private static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(ProviderId.of("holdonly"),
            Set.of(Capability.AUTHORIZE, Capability.CAPTURE, Capability.CANCEL), Set.of(Currency.getInstance("EUR"), Currency.getInstance("USD")),
            RefundTerms.ANY, ProviderDescriptor.Confirmation.SYNC);

    private final SyntheticClient client;

    public HoldOnlyGateway(RestClient.Builder http, String baseUrl) {
        this.client = new SyntheticClient(http, baseUrl);
    }

    @Override
    public ProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public GatewayResult authorize(AuthorizeRequest request) {
        return client.pay(request);
    }

    @Override
    public GatewayResult capture(OperationRequest request) {
        return client.operate("capture", request);
    }

    @Override
    public GatewayResult cancel(OperationRequest request) {
        return client.operate("cancel", request);
    }

    @Override
    public GatewayResult getStatus(String attemptId, ProviderCredentials credentials) {
        return client.status(attemptId, credentials);
    }
}
