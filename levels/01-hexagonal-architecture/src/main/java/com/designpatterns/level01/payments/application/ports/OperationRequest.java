package com.designpatterns.level01.payments.application.ports;

import com.designpatterns.level01.payments.domain.Money;

/**
 * A capture, cancel or refund. {@code operationId} is sent as idempotency key, so re-sending an
 * operation whose outcome is unknown is safe.
 *
 * @param amount null for a cancel
 */
public record OperationRequest(String providerRef, Money amount, String operationId, ProviderCredentials credentials) {
}
