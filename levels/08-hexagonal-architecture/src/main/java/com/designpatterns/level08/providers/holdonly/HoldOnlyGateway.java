package com.designpatterns.level08.providers.holdonly;

import com.designpatterns.level08.payments.application.capabilities.Authorizer;
import com.designpatterns.level08.payments.application.capabilities.Canceller;
import com.designpatterns.level08.payments.application.capabilities.Capability;
import com.designpatterns.level08.payments.application.capabilities.Capturer;
import com.designpatterns.level08.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level08.payments.application.capabilities.ProviderDescriptor;
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
