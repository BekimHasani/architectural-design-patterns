package com.designpatterns.level01.providers.stripe;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Stripe's JSON shapes. Package-private: they never leave this package. */
final class StripeApi {

    private StripeApi() {
    }

    /** {@code capture_method: manual}: hold the funds; money moves only at capture. */
    record CreatePaymentIntent(long amount, String currency, boolean confirm,
                               @JsonProperty("capture_method") String captureMethod, Map<String, String> metadata) {
    }

    record Capture(@JsonProperty("amount_to_capture") long amountToCapture) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Refund(@JsonProperty("payment_intent") String paymentIntent, Long amount) {
    }

    record PaymentIntent(String id, String status, @JsonProperty("next_action") NextAction nextAction) {
    }

    record SearchResult(List<PaymentIntent> data) {
    }

    record NextAction(String type, @JsonProperty("redirect_to_url") RedirectToUrl redirectToUrl) {
    }

    record RedirectToUrl(String url) {
    }

    record ErrorResponse(Error error) {
    }

    record Error(String type, String code, @JsonProperty("decline_code") String declineCode) {
    }
}
