package com.designpatterns.level01.payments.domain;

/** A money rule said no: e.g. capturing more than was authorized, or refunding more than is left. */
public final class PaymentRuleViolationException extends RuntimeException {

    public PaymentRuleViolationException(String message) {
        super(message);
    }
}
