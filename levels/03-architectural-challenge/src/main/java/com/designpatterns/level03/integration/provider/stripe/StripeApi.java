package com.designpatterns.level03.integration.provider.stripe;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** Stripe's JSON shapes. Package-private: they never leave this package. */
final class StripeApi {

    private StripeApi() {
    }

    record CreatePaymentIntent(long amount, String currency, boolean confirm,
                               @JsonProperty("capture_method") String captureMethod, Map<String, String> metadata) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Refund(@JsonProperty("payment_intent") String paymentIntent, long amount, String reason) {
    }

    record PaymentIntent(String id, String status) {
    }

    record RefundResponse(String id, String status) {
    }

    record ErrorResponse(Error error) {
    }

    record Error(String type, String code, @JsonProperty("decline_code") String declineCode) {
    }
}
