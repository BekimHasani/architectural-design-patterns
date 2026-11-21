package com.designpatterns.level02.integration.gateway;

import com.designpatterns.level02.integration.gateway.capability.ActionCompleter;
import com.designpatterns.level02.integration.gateway.capability.Authorizer;
import com.designpatterns.level02.integration.gateway.capability.Canceller;
import com.designpatterns.level02.integration.gateway.capability.Capturer;
import com.designpatterns.level02.integration.gateway.capability.DirectCharger;
import com.designpatterns.level02.integration.gateway.capability.Refunder;
import com.designpatterns.level02.model.Capability;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Links the business word ({@link Capability}, model layer) to the interface that provides it
 * (integration layer). Level 1 kept this inside the enum; a lower layer can't reference an upper
 * one, so the binding lives here, in the layer that owns the interfaces.
 */
public final class CapabilityBinding {

    private static final Map<Capability, Class<?>> TYPES = Map.of(
            Capability.AUTHORIZE, Authorizer.class,
            Capability.CHARGE, DirectCharger.class,
            Capability.CAPTURE, Capturer.class,
            Capability.CANCEL, Canceller.class,
            Capability.REFUND, Refunder.class,
            Capability.CUSTOMER_ACTION, ActionCompleter.class);

    static {
        // A new enum value without an interface stops the app, just like a lying descriptor does.
        if (!TYPES.keySet().equals(EnumSet.allOf(Capability.class))) {
            throw new IllegalStateException("Every Capability needs an interface in CapabilityBinding");
        }
    }

    private CapabilityBinding() {
    }

    public static Class<?> typeOf(Capability capability) {
        return TYPES.get(capability);
    }

    /** The capabilities an adapter really has, read from the interfaces it implements. */
    public static Set<Capability> implementedBy(Object adapter) {
        return Arrays.stream(Capability.values())
                .filter(c -> typeOf(c).isInstance(adapter))
                .collect(Collectors.toUnmodifiableSet());
    }
}
