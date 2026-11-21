package com.designpatterns.level09.service.routing.rules;

import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.service.routing.RoutingContext;
import com.designpatterns.level09.service.routing.RoutingRule;


import java.util.Map;
import java.util.Optional;

/** Enterprise merchants with a contractually agreed provider. */
public final class MerchantOverrideRule implements RoutingRule {

    private final Map<String, ProviderId> providerByMerchant;

    public MerchantOverrideRule(Map<String, ProviderId> providerByMerchant) {
        this.providerByMerchant = Map.copyOf(providerByMerchant);
    }

    @Override
    public Optional<ProviderId> propose(RoutingContext context) {
        return context.merchantId().map(providerByMerchant::get);
    }
}
