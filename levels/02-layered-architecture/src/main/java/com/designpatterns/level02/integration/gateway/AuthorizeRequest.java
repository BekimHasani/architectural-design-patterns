package com.designpatterns.level02.integration.gateway;

import com.designpatterns.level02.model.Money;
import com.designpatterns.level02.model.PaymentId;


/** {@code attemptId} is sent as idempotency key and reference; it is stored before the call. */
public record AuthorizeRequest(PaymentId paymentId, String customerId, Money amount, String attemptId,
                               ProviderCredentials credentials) {
}
