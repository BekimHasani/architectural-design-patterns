package com.designpatterns.level03.model.exceptions;

/** No payment with this id, or it belongs to another merchant (we don't say which). */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String paymentId) {
        super("Payment not found: " + paymentId);
    }
}
