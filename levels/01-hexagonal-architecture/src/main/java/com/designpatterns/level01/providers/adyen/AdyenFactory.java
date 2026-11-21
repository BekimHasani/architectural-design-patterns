package com.designpatterns.level01.providers.adyen;

import com.designpatterns.level01.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level01.providers.support.ProviderFactory;
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
