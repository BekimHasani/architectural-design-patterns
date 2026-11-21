package com.designpatterns.level08.payments.application.ports;

import com.designpatterns.level08.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level08.payments.application.capabilities.ProviderDescriptor;
import com.designpatterns.level08.payments.domain.ProviderId;

import java.util.Collection;
import java.util.function.Function;

/**
 * The way to reach a provider. Every call goes through {@link #call}, which applies the resilience
 * policy, so retry and circuit breaker work for ANY capability without knowing them.
 */
public interface GatewayRegistry {

    ProviderDescriptor descriptor(ProviderId id);

    Collection<ProviderDescriptor> descriptors();

    /** @param capability a capability interface, or {@link ProviderAdapter} for what every provider has */
    <C> GatewayResult call(ProviderId id, Class<C> capability, Function<C, GatewayResult> call);
}
