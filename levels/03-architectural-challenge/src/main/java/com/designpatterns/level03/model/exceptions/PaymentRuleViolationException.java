package com.designpatterns.level03.model.exceptions;

/** A business rule said no (wrong state, refund too large, ...). */
public class PaymentRuleViolationException extends RuntimeException {

    public PaymentRuleViolationException(String message) {
        super(message);
    }
}
