package com.designpatterns.level08.providers.adyen;

import com.designpatterns.level08.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level08.payments.application.ports.ProviderCredentials;
import com.designpatterns.level08.providers.GatewayContractTest;
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
