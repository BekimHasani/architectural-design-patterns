package com.designpatterns.level09.service.routing;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import com.designpatterns.level09.integration.gateway.ProviderDescriptor;
import com.designpatterns.level09.integration.gateway.ProviderGateway;
import com.designpatterns.level09.model.ProviderId;


import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides WHICH provider(s) may take a payment (was ProviderRouter). Pure: no I/O, no adapters, only ids
 * and descriptors (read once from the integration layer's {@link ProviderGateway}).
 *
 * <ul>
 *   <li><b>Precedence</b> = the order of the rules (configured in the composition root):
 *       merchant override, then currency, then default.</li>
 *   <li><b>Conflict</b>: a proposed provider that can't do what this payment needs (currency,
 *       capture mode, required capabilities) is skipped, and the
 *       next rule gets its turn. The merchant's wish is honoured when possible, not at the
 *       price of a failed payment.</li>
 *   <li>The result is an <b>ordered list</b>: the first is used now; Level 4 falls back to the rest.</li>
 * </ul>
 */
@Service
public class ProviderRoutingService {

    private final List<RoutingRule> rules;
    private final Map<ProviderId, ProviderDescriptor> providers;

    /**
     * Spring's constructor: the rules come from config, what providers can do from the providers themselves.
     * With TWO constructors Spring can't guess which one to use, so {@code @Autowired} marks it. The second
     * constructor is for tests: plain lists, no gateway needed.
     */
    @Autowired
    public ProviderRoutingService(RoutingRules rules, ProviderGateway gateway) {
        this(rules.inOrder(), gateway.descriptors());
    }

    public ProviderRoutingService(List<RoutingRule> rules, Collection<ProviderDescriptor> providers) {
        this.rules = List.copyOf(rules);
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(ProviderDescriptor::id, d -> d));
    }

    /**
     * FLOW A.6: ordered, de-duplicated candidates that can take the payment's currency. Empty if none can.
     * Every rule PROPOSES (Strategy), the descriptors FILTER, the order of the rules decides precedence.
     */
    public List<ProviderId> candidates(RoutingContext context) {
        return rules.stream()
                .flatMap(rule -> rule.propose(context).stream())
                .filter(provider -> canTake(provider, context))
                .distinct()
                .toList();
    }

    private boolean canTake(ProviderId provider, RoutingContext context) {
        ProviderDescriptor descriptor = providers.get(provider);
        return descriptor != null
                && descriptor.canTake(context.currency(), context.captureMode(), context.alsoRequired());
    }

    /** Every provider the router may return; the composition root checks they all exist. */
    public Set<ProviderId> knownProviders() {
        return providers.keySet();
    }
}
