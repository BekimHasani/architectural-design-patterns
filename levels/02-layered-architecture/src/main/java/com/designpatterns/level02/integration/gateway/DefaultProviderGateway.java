package com.designpatterns.level02.integration.gateway;

import com.designpatterns.level02.integration.resilience.CallPolicy;
import com.designpatterns.level02.model.Capability;
import com.designpatterns.level02.model.ProviderId;


import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * THE FACADE + REGISTRY over every provider (was InMemoryGatewayRegistry). The service layer reaches
 * providers only through {@link ProviderGateway}; resilience wraps every call here. It holds the
 * providers by id.
 *
 * <p>It REFUSES TO START if a provider's descriptor disagrees with the interfaces it implements: the data
 * the router trusts can't drift from what the code can do.
 *
 * <p>Not a {@code @Component}: it is built in {@code config.IntegrationConfig} (FLOW S.3), because it needs
 * configuration data (which providers are enabled, their URLs, timeouts).
 */
public final class DefaultProviderGateway implements ProviderGateway {

    private final Map<ProviderId, ProviderAdapter> adapters;
    private final Map<ProviderId, CallPolicy> policies;

    public DefaultProviderGateway(Map<ProviderId, ? extends ProviderAdapter> adapters, Map<ProviderId, CallPolicy> policies) {
        this.adapters = Map.copyOf(adapters);
        this.policies = Map.copyOf(policies);
        adapters.values().forEach(DefaultProviderGateway::requireHonestDescriptor);
    }

    public DefaultProviderGateway(Map<ProviderId, ? extends ProviderAdapter> adapters) {
        this(adapters, adapters.keySet().stream().collect(Collectors.toMap(id -> id, id -> java.util.function.Supplier::get)));
    }

    /** The two claims a provider makes about itself must be the same claim. */
    public static void requireHonestDescriptor(ProviderAdapter adapter) {
        ProviderDescriptor descriptor = adapter.descriptor();
        List<Capability> lies = java.util.Arrays.stream(Capability.values())
                .filter(c -> descriptor.supports(c) != CapabilityBinding.typeOf(c).isInstance(adapter))
                .toList();
        if (!lies.isEmpty()) {
            throw new IllegalStateException("Provider " + descriptor.id() + " declares capabilities that don't match its code: " + lies);
        }
    }

    @Override
    public ProviderDescriptor descriptor(ProviderId id) {
        return adapter(id).descriptor();
    }

    @Override
    public Collection<ProviderDescriptor> descriptors() {
        return adapters.values().stream().map(ProviderAdapter::descriptor).toList();
    }

    @Override
    public <C> GatewayResult call(ProviderId id, Class<C> capability, Function<C, GatewayResult> call) {
        // FLOW A.10 / B.5 / D.2: every provider call in the system passes through this line.
        // The caller names the capability interface it needs (e.g. Capturer.class); the cast fails loudly if
        // the provider doesn't implement it (services check the descriptor first, so it shouldn't happen).
        C target = capability.cast(adapter(id));
        // the provider's policy = breaker(retry(...)): the call itself is wrapped, not the adapter
        return policies.get(id).run(() -> call.apply(target));
    }

    private ProviderAdapter adapter(ProviderId id) {
        ProviderAdapter adapter = adapters.get(id);
        if (adapter == null) {
            throw new IllegalArgumentException("No payment provider registered as '" + id + "'");
        }
        return adapter;
    }
}
