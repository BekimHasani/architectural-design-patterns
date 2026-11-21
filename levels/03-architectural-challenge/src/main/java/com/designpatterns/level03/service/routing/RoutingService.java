package com.designpatterns.level03.service.routing;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;

/** Decides which providers to try for a payment, and in which order. */
public class RoutingService {

    private final List<String> defaultOrder;
    private final Map<String, String> preferredByCurrency;

    public RoutingService(List<String> defaultOrder, Map<String, String> preferredByCurrency) {
        this.defaultOrder = List.copyOf(defaultOrder);
        this.preferredByCurrency = Map.copyOf(preferredByCurrency);
    }

    /** The currency's preferred provider first (if any), then the default order, without duplicates. */
    public List<String> candidatesFor(Currency currency) {
        List<String> candidates = new ArrayList<>();
        String preferred = preferredByCurrency.get(currency.getCurrencyCode());
        if (preferred != null) {
            candidates.add(preferred);
        }
        for (String providerId : defaultOrder) {
            if (!candidates.contains(providerId)) {
                candidates.add(providerId);
            }
        }
        return candidates;
    }
}
