package com.designpatterns.level02.integration.provider.holdonly;

import com.designpatterns.level02.integration.gateway.ProviderAdapter;
import com.designpatterns.level02.integration.gateway.ProviderFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class HoldOnlyFactory implements ProviderFactory {

    @Override
    public String name() {
        return "holdonly";
    }

    @Override
    public ProviderAdapter create(RestClient.Builder http, String baseUrl) {
        return new HoldOnlyGateway(http, baseUrl);
    }
}
