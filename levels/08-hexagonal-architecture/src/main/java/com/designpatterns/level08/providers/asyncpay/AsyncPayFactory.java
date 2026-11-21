package com.designpatterns.level08.providers.asyncpay;

import com.designpatterns.level08.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level08.providers.support.ProviderFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class AsyncPayFactory implements ProviderFactory {

    @Override
    public String name() {
        return "asyncpay";
    }

    @Override
    public ProviderAdapter create(RestClient.Builder http, String baseUrl) {
        return new AsyncPayGateway(http, baseUrl);
    }
}
