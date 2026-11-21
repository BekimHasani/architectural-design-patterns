package com.designpatterns.level01.payments.application.commands;

import com.designpatterns.level01.payments.domain.Payment;
import com.designpatterns.level01.payments.domain.PaymentAttempt;

import java.util.List;

/**
 * What the use cases return: the payment as stored, and every attempt so far.
 *
 * @param operationId the capture, cancel or refund THIS command ran, or null. Not "the last one":
 *                    in a race, the last operation on the payment may be another request's.
 */
public record PaymentResult(Payment payment, List<PaymentAttempt> attempts, String operationId) {

    public PaymentResult(Payment payment, List<PaymentAttempt> attempts) {
        this(payment, attempts, null);
    }

    public PaymentResult {
        attempts = List.copyOf(attempts);
    }
}
