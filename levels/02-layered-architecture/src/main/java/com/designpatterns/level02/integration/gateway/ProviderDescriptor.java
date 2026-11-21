package com.designpatterns.level02.integration.gateway;

import com.designpatterns.level02.model.Capability;
import com.designpatterns.level02.model.CaptureMode;
import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.model.RefundTerms;


import java.util.Currency;
import java.util.EnumSet;
import java.util.Set;

/**
 * What a provider can do, as DATA: the router reads this to choose BEFORE calling anyone. The
 * interfaces say what code CAN call; this says who to ask. The registry refuses to start if the two
 * disagree, so the descriptor can't drift from the code.
 *
 * @param confirmation how results arrive
 */
public record ProviderDescriptor(ProviderId id, Set<Capability> capabilities, Set<Currency> currencies,
                                 RefundTerms refundTerms, Confirmation confirmation) {

    public enum Confirmation { SYNC, ASYNC, BOTH }

    public ProviderDescriptor {
        capabilities = capabilities.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(capabilities));
        currencies = Set.copyOf(currencies);
    }

    public boolean supports(Capability capability) {
        return capabilities.contains(capability);
    }

    /** Can this provider carry a payment of this currency and capture mode, with these extra needs? */
    public boolean canTake(Currency currency, CaptureMode mode, Set<Capability> alsoRequired) {
        return currencies.contains(currency) && capabilities.containsAll(alsoRequired) && canDo(mode);
    }

    private boolean canDo(CaptureMode mode) {
        boolean authThenCapture = supports(Capability.AUTHORIZE) && supports(Capability.CAPTURE);
        return switch (mode) {
            case MANUAL -> authThenCapture && supports(Capability.CANCEL);
            case AUTOMATIC -> authThenCapture || supports(Capability.CHARGE);
        };
    }
}
