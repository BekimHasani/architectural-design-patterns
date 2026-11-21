package com.designpatterns.level02.model;

public record PaymentEvent(String type, PaymentStatus from, PaymentStatus to, Long amountMinor,
                           String actor, String detail) {
}
