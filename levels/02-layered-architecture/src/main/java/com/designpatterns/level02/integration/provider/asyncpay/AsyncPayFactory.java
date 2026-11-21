package com.designpatterns.level02.integration.provider.asyncpay;

import com.designpatterns.level02.integration.gateway.ProviderAdapter;
import com.designpatterns.level02.integration.gateway.ProviderFactory;

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
