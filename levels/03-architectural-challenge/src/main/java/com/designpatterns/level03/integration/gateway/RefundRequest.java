package com.designpatterns.level03.integration.gateway;

import com.designpatterns.level03.model.Money;

/**
 * @param operationId       our id for this refund; sent to the provider as its idempotency key
 * @param providerReference the provider's id of the original charge
 */
public record RefundRequest(String operationId, String providerReference, Money amount) {
}
