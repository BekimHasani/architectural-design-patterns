package com.designpatterns.level03.presentation.rest.dto;

public record CreatePaymentRequest(String customerId, String currency, Long amountMinor) {
}
