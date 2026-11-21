package com.designpatterns.level01.payments.application.commands;

import com.designpatterns.level01.payments.application.capabilities.Capability;
import com.designpatterns.level01.payments.domain.ProviderId;

/** The provider holding this payment can't do that. Decided from its descriptor; no request was sent. */
public final class OperationNotSupportedException extends RuntimeException {

    public OperationNotSupportedException(ProviderId providerId, Capability capability) {
        super("Provider " + providerId + " does not support " + capability.name().toLowerCase()
                + ". Handle it outside the platform (e.g. a manual payout).");
    }
}
