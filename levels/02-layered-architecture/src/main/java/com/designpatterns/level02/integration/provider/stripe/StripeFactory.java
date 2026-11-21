package com.designpatterns.level02.integration.provider.stripe;

import com.designpatterns.level02.integration.gateway.ProviderAdapter;
import com.designpatterns.level02.integration.gateway.ProviderFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class StripeFactory implements ProviderFactory {

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public ProviderAdapter create(RestClient.Builder http, String baseUrl) {
        return new StripeGateway(http, baseUrl);
    }
}
