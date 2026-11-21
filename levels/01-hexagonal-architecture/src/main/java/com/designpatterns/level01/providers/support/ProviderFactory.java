package com.designpatterns.level01.providers.support;

import com.designpatterns.level01.payments.application.capabilities.ProviderAdapter;
import org.springframework.web.client.RestClient;

/**
 * A provider's self-registration: put a {@code @Component} implementing this in the provider's
 * folder, and enable its name in {@code payment.enabled}. Nothing else in the system names it.
 */
public interface ProviderFactory {

    String name();

    ProviderAdapter create(RestClient.Builder http, String baseUrl);
}
