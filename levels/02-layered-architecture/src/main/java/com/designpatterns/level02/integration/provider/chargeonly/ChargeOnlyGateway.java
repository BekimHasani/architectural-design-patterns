package com.designpatterns.level02.integration.provider.chargeonly;

import com.designpatterns.level02.integration.gateway.AuthorizeRequest;
import com.designpatterns.level02.integration.gateway.GatewayResult;
import com.designpatterns.level02.integration.gateway.ProviderAdapter;
import com.designpatterns.level02.integration.gateway.ProviderCredentials;
import com.designpatterns.level02.integration.gateway.ProviderDescriptor;
import com.designpatterns.level02.integration.gateway.capability.DirectCharger;
import com.designpatterns.level02.integration.support.SyntheticClient;
import com.designpatterns.level02.model.Capability;
import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.model.RefundTerms;

import org.springframework.web.client.RestClient;

import java.util.Currency;
import java.util.Set;

/** Takes money directly. That is all it does. */
public final class ChargeOnlyGateway implements ProviderAdapter, DirectCharger {

    private static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(ProviderId.of("chargeonly"),
            Set.of(Capability.CHARGE), Set.of(Currency.getInstance("EUR"), Currency.getInstance("USD")),
            RefundTerms.ANY, ProviderDescriptor.Confirmation.SYNC);

    private final SyntheticClient client;

    public ChargeOnlyGateway(RestClient.Builder http, String baseUrl) {
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
    public GatewayResult getStatus(String attemptId, ProviderCredentials credentials) {
        return client.status(attemptId, credentials);
    }
}
