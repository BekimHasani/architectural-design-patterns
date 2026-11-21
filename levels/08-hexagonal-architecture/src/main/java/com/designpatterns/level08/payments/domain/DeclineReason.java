package com.designpatterns.level08.payments.domain;

/** Why a payment was refused, in our vocabulary. Each adapter maps its provider's codes onto these. */
public enum DeclineReason {
    INSUFFICIENT_FUNDS,
    SUSPECTED_FRAUD,
    /** The provider said no without a reason we model. */
    GENERIC
}
