package com.designpatterns.level02.model.exceptions;

public class PaymentRuleViolationException extends RuntimeException {
    public PaymentRuleViolationException(String message) {
        super(message);
    }
}
