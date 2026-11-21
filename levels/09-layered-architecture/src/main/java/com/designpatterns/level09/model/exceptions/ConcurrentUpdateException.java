package com.designpatterns.level09.model.exceptions;

import com.designpatterns.level09.model.PaymentId;

/**
 * Two writers raced on the same payment; the loser gets this instead of overwriting the winner.
 *
 * <p>Why in the MODEL and not in persistence: the version is part of the aggregate
 * ({@code Payment.version()}), and three layers react to this exception: persistence throws it,
 * services retry on it, presentation answers 409. Only the shared bottom layer can be seen by all
 * three without breaking "dependencies only point down" (presentation may not import persistence).
 */
public final class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(PaymentId paymentId) {
        super("Payment " + paymentId + " was changed concurrently");
    }
}
