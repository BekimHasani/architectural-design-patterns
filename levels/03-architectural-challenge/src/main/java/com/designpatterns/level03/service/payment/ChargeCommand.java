package com.designpatterns.level03.service.payment;

import com.designpatterns.level03.model.Money;

public record ChargeCommand(String merchantId, String customerId, Money amount) {
}
