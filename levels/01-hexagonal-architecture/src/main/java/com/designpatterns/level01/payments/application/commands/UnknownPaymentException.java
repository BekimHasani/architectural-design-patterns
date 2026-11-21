package com.designpatterns.level01.payments.application.commands;

import com.designpatterns.level01.payments.domain.PaymentId;

/** Doesn't exist, or belongs to another merchant: both look the same from outside, on purpose. */
public final class UnknownPaymentException extends RuntimeException {

    public UnknownPaymentException(PaymentId paymentId) {
        super("No payment " + paymentId);
    }
}
