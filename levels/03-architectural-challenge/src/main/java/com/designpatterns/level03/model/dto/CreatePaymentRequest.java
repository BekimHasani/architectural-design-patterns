package com.designpatterns.level03.model.dto;

public record CreatePaymentRequest(String customerId, String currency, Long amountMinor) {
}
