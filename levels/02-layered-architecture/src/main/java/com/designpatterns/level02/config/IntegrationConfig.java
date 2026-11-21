package com.designpatterns.level02.config;

import com.designpatterns.level02.integration.gateway.DefaultProviderGateway;
import com.designpatterns.level02.integration.gateway.ProviderAdapter;
import com.designpatterns.level02.integration.gateway.ProviderFactory;
import com.designpatterns.level02.integration.gateway.ProviderGateway;
import com.designpatterns.level02.integration.resilience.CallPolicy;
import com.designpatterns.level02.integration.resilience.CircuitBreakerPolicy;
import com.designpatterns.level02.integration.resilience.RetryPolicy;
import com.designpatterns.level02.integration.support.ProviderHttp;
import com.designpatterns.level02.model.ProviderId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Integration wiring: the provider gateway. NAMES NO PROVIDER: they register through {@link ProviderFactory}. */
@Configuration
class IntegrationConfig {

    /**
     * FLOW S.3: every enabled provider, built ONCE (PLUG-IN + FACTORY). Every call runs through
     * breaker( retry( call ) ) (DECORATOR), whatever capability it uses.
     *
     * <p>{@code List<ProviderFactory> factories}: Spring injects EVERY bean of that type it found by
     * scanning (one {@code @Component} factory per provider folder). That is how this method builds the
     * providers without naming any of them.
     */
    @Bean
    ProviderGateway providerGateway(PaymentProperties p, List<ProviderFactory> factories, RestClient.Builder restClient,
                                    Clock clock) {
        PaymentProperties.Resilience r = p.resilience();
        Map<String, ProviderFactory> byName = factories.stream()
                .collect(Collectors.toMap(ProviderFactory::name, Function.identity()));
        Map<ProviderId, ProviderAdapter> adapters = new HashMap<>();
        Map<ProviderId, CallPolicy> policies = new HashMap<>();
        for (String name : p.enabled()) {                       // only providers listed in payment.enabled
            ProviderFactory factory = byName.get(name);
            if (factory == null) {                              // FAIL FAST: a typo stops the app at startup
                throw new IllegalStateException("Unknown provider in payment.enabled: " + name);
            }
            // each provider gets its own HTTP client with timeouts: a hanging provider must not hang us
            RestClient.Builder http = ProviderHttp.withTimeouts(restClient.clone(), r.connectTimeout(), r.readTimeout());
            adapters.put(ProviderId.of(name), factory.create(http, p.baseUrls().get(name)));
            // one breaker PER provider: Stripe being down must not block PayPal
            CallPolicy retry = new RetryPolicy(r.maxAttempts(), r.retryBaseDelay());
            CallPolicy breaker = new CircuitBreakerPolicy(r.breakerFailureThreshold(), r.breakerOpenFor(), clock);
            // DECORATOR order matters: the breaker sees the result AFTER the retries, so a call that
            // succeeded on its second try doesn't count as a failure
            policies.put(ProviderId.of(name), call -> breaker.run(() -> retry.run(call)));
        }
        return new DefaultProviderGateway(adapters, policies); // checks every descriptor is honest (fail fast)
    }
}
