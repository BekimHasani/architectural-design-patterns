package com.designpatterns.level09.model.exceptions;

public class PaymentRuleViolationException extends RuntimeException {
    public PaymentRuleViolationException(String message) {
        super(message);
    }
}
