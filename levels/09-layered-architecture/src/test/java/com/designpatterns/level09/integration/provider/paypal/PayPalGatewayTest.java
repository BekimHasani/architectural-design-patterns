package com.designpatterns.level09.integration.provider.paypal;

import com.designpatterns.level09.integration.gateway.AuthorizeRequest;
import com.designpatterns.level09.integration.gateway.ProviderAdapter;
import com.designpatterns.level09.integration.gateway.ProviderCredentials;
import com.designpatterns.level09.integration.provider.GatewayContractTest;
import com.designpatterns.level09.model.PaymentId;

import com.designpatterns.simulators.PayPalLikeSimulator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayPalGatewayTest extends GatewayContractTest<PayPalLikeSimulator> {

    @Override
    protected PayPalLikeSimulator startSimulator() throws IOException {
        return PayPalLikeSimulator.start(0);
    }

    @Override
    protected ProviderAdapter gateway(PayPalLikeSimulator sim) {
        return new PayPalGateway(http(), sim.baseUrl());
    }

    @Override
    protected ProviderCredentials credentials() {
        return new ProviderCredentials(Map.of("client-id", PayPalLikeSimulator.CLIENT_ID, "client-secret", PayPalLikeSimulator.CLIENT_SECRET));
    }

    @Override
    protected ProviderCredentials badCredentials() {
        return new ProviderCredentials(Map.of("client-id", "wrong", "client-secret", "wrong"));
    }

    /** One token per merchant client, reused. */
    @Test
    void reusesTheTokenPerClient() throws IOException {
        try (PayPalLikeSimulator paypal = startSimulator()) {
            PayPalGateway gateway = new PayPalGateway(http(), paypal.baseUrl());
            gateway.authorize(request("att_1", credentials()));
            gateway.authorize(request("att_2", credentials()));
            assertThat(paypal.tokenRequests()).isEqualTo(1);

            gateway.authorize(request("att_3", new ProviderCredentials(Map.of("client-id", "other", "client-secret", "sim-secret-other"))));
            assertThat(paypal.tokenRequests()).as("a second merchant gets its own token").isEqualTo(2);
        }
    }

    private static AuthorizeRequest request(String attemptId, ProviderCredentials credentials) {
        return new AuthorizeRequest(PaymentId.newId(), "c-1", AMOUNT, attemptId, credentials);
    }
}
