package com.designpatterns.level09.integration.gateway;

import com.designpatterns.level09.model.Money;
import com.designpatterns.level09.model.PaymentId;


/** {@code attemptId} is sent as idempotency key and reference; it is stored before the call. */
public record AuthorizeRequest(PaymentId paymentId, String customerId, Money amount, String attemptId,
                               ProviderCredentials credentials) {
}
