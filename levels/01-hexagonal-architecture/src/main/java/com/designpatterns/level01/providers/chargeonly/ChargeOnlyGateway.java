package com.designpatterns.level01.providers.chargeonly;

import com.designpatterns.level01.payments.application.capabilities.Capability;
import com.designpatterns.level01.payments.application.capabilities.DirectCharger;
import com.designpatterns.level01.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level01.payments.application.capabilities.ProviderDescriptor;
import com.designpatterns.level01.payments.application.ports.AuthorizeRequest;
import com.designpatterns.level01.payments.application.ports.GatewayResult;
import com.designpatterns.level01.payments.application.ports.ProviderCredentials;
import com.designpatterns.level01.payments.domain.ProviderId;
import com.designpatterns.level01.payments.domain.RefundTerms;
import com.designpatterns.level01.providers.support.SyntheticClient;
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
