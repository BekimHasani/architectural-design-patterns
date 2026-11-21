package com.designpatterns.simulators;

import java.util.ArrayList;
import java.util.List;

/**
 * What the provider REALLY charged. Tests read it via {@link BaseSimulator#ledger()} or
 * {@code GET /sim/ledger} to prove things like "the customer was charged exactly once".
 */
public final class Ledger {

    /** Amounts are always recorded in minor units, whatever format the provider's API uses. */
    public record Entry(String providerRef, long amountMinor, String currency) {
    }

    private final List<Entry> entries = new ArrayList<>();

    synchronized void record(String providerRef, long amountMinor, String currency) {
        entries.add(new Entry(providerRef, amountMinor, currency));
    }

    synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    synchronized void clear() {
        entries.clear();
    }
}
