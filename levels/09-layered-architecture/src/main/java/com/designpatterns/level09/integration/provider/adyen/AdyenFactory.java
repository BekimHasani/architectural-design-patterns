package com.designpatterns.level09.integration.provider.adyen;

import com.designpatterns.level09.integration.gateway.ProviderAdapter;
import com.designpatterns.level09.integration.gateway.ProviderFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class AdyenFactory implements ProviderFactory {

    @Override
    public String name() {
        return "adyen";
    }

    @Override
    public ProviderAdapter create(RestClient.Builder http, String baseUrl) {
        return new AdyenGateway(http, baseUrl);
    }
}
