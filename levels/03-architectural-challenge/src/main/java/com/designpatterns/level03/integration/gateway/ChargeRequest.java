package com.designpatterns.level03.integration.gateway;

import com.designpatterns.level03.model.Money;

/** @param attemptId our id for this try; sent to the provider as its idempotency key and reference */
public record ChargeRequest(String attemptId, Money amount) {
}
