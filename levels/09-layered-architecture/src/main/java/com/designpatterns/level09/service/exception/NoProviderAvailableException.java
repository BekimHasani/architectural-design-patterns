package com.designpatterns.level09.service.exception;

import com.designpatterns.level09.model.Capability;
import com.designpatterns.level09.model.CaptureMode;


import java.util.Currency;
import java.util.Set;

/** No provider can take this payment for this merchant. Nothing was charged. */
public final class NoProviderAvailableException extends RuntimeException {

    public NoProviderAvailableException(Currency currency, CaptureMode mode, Set<Capability> alsoRequired) {
        super("No payment provider available to this merchant can take a " + currency + " payment with capture mode "
                + mode + (alsoRequired.isEmpty() ? "" : " and also do " + alsoRequired));
    }
}
