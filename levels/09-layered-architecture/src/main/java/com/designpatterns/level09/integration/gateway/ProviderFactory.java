package com.designpatterns.level09.integration.gateway;

import org.springframework.web.client.RestClient;

/**
 * A provider's self-registration (PLUG-IN + FACTORY): put a {@code @Component} implementing this in the
 * provider's folder, and enable its name in {@code payment.enabled}. Nothing else in the system names it.
 *
 * <p>Why a factory and not the adapter itself as a {@code @Component}? The adapter needs things only the
 * composition root knows (base URL, an HTTP client with timeouts). The factory is the scanned plug-in; the
 * adapter is built later, in {@code IntegrationConfig}, only if the provider is enabled (FLOW S.3).
 */
public interface ProviderFactory {

    String name();

    ProviderAdapter create(RestClient.Builder http, String baseUrl);
}
