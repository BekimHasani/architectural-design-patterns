package com.designpatterns.level09.integration.provider.chargeonly;

import com.designpatterns.level09.integration.gateway.ProviderAdapter;
import com.designpatterns.level09.integration.gateway.ProviderCredentials;
import com.designpatterns.level09.integration.provider.GatewayContractTest;

import com.designpatterns.simulators.SyntheticSimulator;

import java.io.IOException;

class ChargeOnlyGatewayTest extends GatewayContractTest<SyntheticSimulator> {

    @Override
    protected SyntheticSimulator startSimulator() throws IOException {
        return SyntheticSimulator.start(0, SyntheticSimulator.Mode.CHARGE);
    }

    @Override
    protected ProviderAdapter gateway(SyntheticSimulator sim) {
        return new ChargeOnlyGateway(http(), sim.baseUrl());
    }

    @Override
    protected ProviderCredentials credentials() {
        return ProviderCredentials.of("key", "syn_contract");
    }

    @Override
    protected ProviderCredentials badCredentials() {
        return ProviderCredentials.of("key", "wrong");
    }
}
