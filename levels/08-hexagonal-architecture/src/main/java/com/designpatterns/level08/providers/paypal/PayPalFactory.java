package com.designpatterns.level08.providers.paypal;

import com.designpatterns.level08.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level08.providers.support.ProviderFactory;
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
