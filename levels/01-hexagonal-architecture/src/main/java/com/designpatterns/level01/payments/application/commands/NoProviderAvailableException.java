package com.designpatterns.level01.payments.application.commands;

import com.designpatterns.level01.payments.application.capabilities.Capability;
import com.designpatterns.level01.payments.domain.CaptureMode;

import java.util.Currency;
import java.util.Set;

/** No provider can take this payment for this merchant. Nothing was charged. */
public final class NoProviderAvailableException extends RuntimeException {

    public NoProviderAvailableException(Currency currency, CaptureMode mode, Set<Capability> alsoRequired) {
        super("No payment provider available to this merchant can take a " + currency + " payment with capture mode "
                + mode + (alsoRequired.isEmpty() ? "" : " and also do " + alsoRequired));
    }
}
