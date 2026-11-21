package com.designpatterns.simulators;

/**
 * Sticky fault mode, set via {@code POST /sim/faults {"mode": "decline", "count": 1}} or
 * {@link BaseSimulator#injectFault}. A count of 0 or less means "until reset".
 */
final class FaultInjector {

    private Fault mode = Fault.NONE;
    private int remaining;

    synchronized void set(Fault mode, int count) {
        this.mode = mode;
        this.remaining = count;
    }

    /** Returns the fault for the current request and consumes one use of it. */
    synchronized Fault next() {
        Fault current = mode;
        if (current != Fault.NONE && remaining > 0 && --remaining == 0) {
            mode = Fault.NONE;
        }
        return current;
    }

    synchronized void reset() {
        set(Fault.NONE, 0);
    }
}
