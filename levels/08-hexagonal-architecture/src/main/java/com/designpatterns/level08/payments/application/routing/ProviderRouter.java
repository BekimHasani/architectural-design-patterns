package com.designpatterns.level08.payments.application.routing;

import com.designpatterns.level08.payments.application.capabilities.ProviderDescriptor;
import com.designpatterns.level08.payments.domain.ProviderId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides WHICH provider(s) may take a payment. Pure: no I/O, no adapters, only ids.
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
public final class ProviderRouter {

    private final List<RoutingRule> rules;
    private final Map<ProviderId, ProviderDescriptor> providers;

    public ProviderRouter(List<RoutingRule> rules, Collection<ProviderDescriptor> providers) {
        this.rules = List.copyOf(rules);
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(ProviderDescriptor::id, d -> d));
    }

    /** Ordered, de-duplicated candidates that can take the payment's currency. Empty if none can. */
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
