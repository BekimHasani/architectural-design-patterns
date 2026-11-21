package com.designpatterns.level01.providers;

import com.designpatterns.level01.payments.application.capabilities.Capability;
import com.designpatterns.level01.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level01.payments.application.capabilities.ProviderDescriptor;
import com.designpatterns.level01.payments.application.ports.CallPolicy;
import com.designpatterns.level01.payments.application.ports.GatewayRegistry;
import com.designpatterns.level01.payments.application.ports.GatewayResult;
import com.designpatterns.level01.payments.domain.ProviderId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The providers, by id. It REFUSES TO START if a provider's descriptor disagrees with the interfaces
 * it implements: the data the router trusts can't drift from what the code can do.
 */
public final class InMemoryGatewayRegistry implements GatewayRegistry {

    private final Map<ProviderId, ProviderAdapter> adapters;
    private final Map<ProviderId, CallPolicy> policies;

    public InMemoryGatewayRegistry(Map<ProviderId, ? extends ProviderAdapter> adapters, Map<ProviderId, CallPolicy> policies) {
        this.adapters = Map.copyOf(adapters);
        this.policies = Map.copyOf(policies);
        adapters.values().forEach(InMemoryGatewayRegistry::requireHonestDescriptor);
    }

    public InMemoryGatewayRegistry(Map<ProviderId, ? extends ProviderAdapter> adapters) {
        this(adapters, adapters.keySet().stream().collect(Collectors.toMap(id -> id, id -> java.util.function.Supplier::get)));
    }

    /** The two claims a provider makes about itself must be the same claim. */
    public static void requireHonestDescriptor(ProviderAdapter adapter) {
        ProviderDescriptor descriptor = adapter.descriptor();
        List<Capability> lies = java.util.Arrays.stream(Capability.values())
                .filter(c -> descriptor.supports(c) != c.type().isInstance(adapter))
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
        C target = capability.cast(adapter(id));
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
