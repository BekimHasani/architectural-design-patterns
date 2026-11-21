package com.designpatterns.level08.payments.application.commands;

import com.designpatterns.level08.payments.domain.PaymentId;

/** Doesn't exist, or belongs to another merchant: both look the same from outside, on purpose. */
public final class UnknownPaymentException extends RuntimeException {

    public UnknownPaymentException(PaymentId paymentId) {
        super("No payment " + paymentId);
    }
}
