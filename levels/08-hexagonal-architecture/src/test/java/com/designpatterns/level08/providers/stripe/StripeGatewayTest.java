package com.designpatterns.level08.providers.stripe;

import com.designpatterns.level08.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level08.payments.application.ports.ProviderCredentials;
import com.designpatterns.level08.providers.GatewayContractTest;
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
