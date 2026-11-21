package com.designpatterns.level02.integration.provider.providerx;

import com.designpatterns.level02.integration.gateway.ProviderAdapter;
import com.designpatterns.level02.integration.gateway.ProviderFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class ProviderXFactory implements ProviderFactory {

    @Override
    public String name() {
        return "providerx";
    }

    @Override
    public ProviderAdapter create(RestClient.Builder http, String baseUrl) {
        return new ProviderXGateway(http, baseUrl);
    }
}
