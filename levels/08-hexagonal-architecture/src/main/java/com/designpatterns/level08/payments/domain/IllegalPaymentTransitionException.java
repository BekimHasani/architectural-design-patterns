package com.designpatterns.level08.payments.domain;

public final class IllegalPaymentTransitionException extends RuntimeException {

    public IllegalPaymentTransitionException(PaymentId paymentId, PaymentStatus from, String event) {
        super("Payment " + paymentId + " cannot '" + event + "' while " + from);
    }
}
