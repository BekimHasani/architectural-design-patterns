package com.designpatterns.level09.integration.provider.providerx;

import com.designpatterns.level09.integration.gateway.ProviderAdapter;
import com.designpatterns.level09.integration.gateway.ProviderCredentials;
import com.designpatterns.level09.integration.provider.GatewayContractTest;

import com.designpatterns.simulators.ProviderXSimulator;

import java.io.IOException;

/** The same contract as Stripe, PayPal and Adyen. */
class ProviderXGatewayTest extends GatewayContractTest<ProviderXSimulator> {

    @Override
    protected ProviderXSimulator startSimulator() throws IOException {
        return ProviderXSimulator.start(0);
    }

    @Override
    protected ProviderAdapter gateway(ProviderXSimulator sim) {
        return new ProviderXGateway(http(), sim.baseUrl());
    }

    @Override
    protected ProviderCredentials credentials() {
        return ProviderCredentials.of("token", "xt_contract");
    }

    @Override
    protected ProviderCredentials badCredentials() {
        return ProviderCredentials.of("token", "wrong");
    }
}
