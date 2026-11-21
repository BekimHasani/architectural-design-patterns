package com.designpatterns.level02.integration.provider.adyen;

import com.designpatterns.level02.integration.gateway.ProviderAdapter;
import com.designpatterns.level02.integration.gateway.ProviderCredentials;
import com.designpatterns.level02.integration.provider.GatewayContractTest;

import com.designpatterns.simulators.AdyenLikeSimulator;

import java.io.IOException;
import java.util.Map;

class AdyenGatewayTest extends GatewayContractTest<AdyenLikeSimulator> {

    @Override
    protected AdyenLikeSimulator startSimulator() throws IOException {
        return AdyenLikeSimulator.start(0);
    }

    @Override
    protected ProviderAdapter gateway(AdyenLikeSimulator sim) {
        return new AdyenGateway(http(), sim.baseUrl());
    }

    @Override
    protected ProviderCredentials credentials() {
        return new ProviderCredentials(Map.of("api-key", "test_contract", "merchant-account", "ContractShop"));
    }

    @Override
    protected ProviderCredentials badCredentials() {
        return new ProviderCredentials(Map.of("api-key", "wrong", "merchant-account", "ContractShop"));
    }
}
