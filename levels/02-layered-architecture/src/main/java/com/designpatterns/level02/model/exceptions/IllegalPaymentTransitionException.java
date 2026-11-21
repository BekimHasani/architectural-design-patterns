package com.designpatterns.level02.model.exceptions;

import com.designpatterns.level02.model.PaymentId;
import com.designpatterns.level02.model.PaymentStatus;

public final class IllegalPaymentTransitionException extends RuntimeException {

    public IllegalPaymentTransitionException(PaymentId paymentId, PaymentStatus from, String event) {
        super("Payment " + paymentId + " cannot '" + event + "' while " + from);
    }
}