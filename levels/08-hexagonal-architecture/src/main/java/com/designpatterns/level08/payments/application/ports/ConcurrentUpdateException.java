package com.designpatterns.level08.payments.application.ports;

import com.designpatterns.level08.payments.domain.PaymentId;

/** Two writers raced on the same payment; the loser gets this instead of overwriting the winner. */
public final class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(PaymentId orderId) {
        super("Payment " + orderId + " was changed concurrently");
    }
}
