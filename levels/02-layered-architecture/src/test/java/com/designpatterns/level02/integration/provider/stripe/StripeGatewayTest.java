package com.designpatterns.level02.integration.provider.stripe;

import com.designpatterns.level02.integration.gateway.ProviderAdapter;
import com.designpatterns.level02.integration.gateway.ProviderCredentials;
import com.designpatterns.level02.integration.provider.GatewayContractTest;

import com.designpatterns.simulators.StripeLikeSimulator;

import java.io.IOException;

class StripeGatewayTest extends GatewayContractTest<StripeLikeSimulator> {

    @Override
    protected StripeLikeSimulator startSimulator() throws IOException {
        return StripeLikeSimulator.start(0);
    }

    @Override
    protected ProviderAdapter gateway(StripeLikeSimulator sim) {
        return new StripeGateway(http(), sim.baseUrl());
    }

    @Override
    protected ProviderCredentials credentials() {
        return ProviderCredentials.of("api-key", "sk_test_contract");
    }

    @Override
    protected ProviderCredentials badCredentials() {
        return ProviderCredentials.of("api-key", "wrong");
    }
}
