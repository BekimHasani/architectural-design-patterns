package com.designpatterns.level09.integration.provider.paypal;

import com.designpatterns.level09.integration.gateway.ProviderAdapter;
import com.designpatterns.level09.integration.gateway.ProviderFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class PayPalFactory implements ProviderFactory {

    @Override
    public String name() {
        return "paypal";
    }

    @Override
    public ProviderAdapter create(RestClient.Builder http, String baseUrl) {
        return new PayPalGateway(http, baseUrl);
    }
}
