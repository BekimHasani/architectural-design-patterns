package com.designpatterns.level08.providers.holdonly;

import com.designpatterns.level08.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level08.providers.support.ProviderFactory;
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
