package com.designpatterns.level08.payments.application.ports;

import com.designpatterns.level08.payments.domain.Money;
import com.designpatterns.level08.payments.domain.PaymentId;

/** {@code attemptId} is sent as idempotency key and reference; it is stored before the call. */
public record AuthorizeRequest(PaymentId paymentId, String customerId, Money amount, String attemptId,
                               ProviderCredentials credentials) {
}
