package com.designpatterns.level09.integration.provider.chargeonly;

import com.designpatterns.level09.integration.gateway.ProviderAdapter;
import com.designpatterns.level09.integration.gateway.ProviderFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class ChargeOnlyFactory implements ProviderFactory {

    @Override
    public String name() {
        return "chargeonly";
    }

    @Override
    public ProviderAdapter create(RestClient.Builder http, String baseUrl) {
        return new ChargeOnlyGateway(http, baseUrl);
    }
}
