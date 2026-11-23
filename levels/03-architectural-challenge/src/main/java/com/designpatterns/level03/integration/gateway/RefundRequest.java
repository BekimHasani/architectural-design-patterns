package com.designpatterns.level03.integration.gateway;

import com.designpatterns.level03.model.Money;

/**
 * @param operationId       our id for this refund; sent to the provider as its idempotency key
 * @param providerReference the provider's id of the original charge
 * @param reason            why the merchant refunds, may be null. Part of the contract so the service never
 *                          needs to know which provider can show it: each adapter decides whether to send it.
 */
public record RefundRequest(String operationId, String providerReference, Money amount, String reason) {

    /** A refund without a reason. */
    public RefundRequest(String operationId, String providerReference, Money amount) {
        this(operationId, providerReference, amount, null);
    }
}
