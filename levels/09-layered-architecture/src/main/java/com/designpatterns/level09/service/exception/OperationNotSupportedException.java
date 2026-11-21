package com.designpatterns.level09.service.exception;

import com.designpatterns.level09.model.Capability;
import com.designpatterns.level09.model.ProviderId;


/** The provider holding this payment can't do that. Decided from its descriptor; no request was sent. */
public final class OperationNotSupportedException extends RuntimeException {

    public OperationNotSupportedException(ProviderId providerId, Capability capability) {
        super("Provider " + providerId + " does not support " + capability.name().toLowerCase()
                + ". Handle it outside the platform (e.g. a manual payout).");
    }
}
