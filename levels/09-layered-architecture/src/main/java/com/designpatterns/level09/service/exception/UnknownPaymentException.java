package com.designpatterns.level09.service.exception;

import com.designpatterns.level09.model.PaymentId;


/** Doesn't exist, or belongs to another merchant: both look the same from outside, on purpose. */
public final class UnknownPaymentException extends RuntimeException {

    public UnknownPaymentException(PaymentId paymentId) {
        super("No payment " + paymentId);
    }
}
