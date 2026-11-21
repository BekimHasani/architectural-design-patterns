package com.designpatterns.level03.presentation.rest.dto;

import com.designpatterns.level03.model.Payment;

/** The JSON shape of a payment. */
public record PaymentResponse(String id, String merchantId, String customerId, long amountMinor, String currency,
                              String status, String providerId, String providerReference, long refundedMinor,
                              String failureReason) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.id(), payment.merchantId(), payment.customerId(),
                payment.amount().amountMinor(), payment.amount().currency().getCurrencyCode(), payment.status().name(),
                payment.providerId(), payment.providerReference(), payment.refunded().amountMinor(),
                payment.failureReason());
    }
}
