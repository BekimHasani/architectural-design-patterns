package com.designpatterns.level03.integration.provider.adyen;

/** Adyen's JSON shapes. Package-private: they never leave this package. */
final class AdyenApi {

    private AdyenApi() {
    }

    record PaymentRequest(Amount amount, String reference, String merchantAccount, boolean manualCapture) {
    }

    record Amount(long value, String currency) {
    }

    record ModificationRequest(Amount amount, String merchantAccount, String reference) {
    }

    /** Arrives with HTTP 200 for Authorised AND for Refused: the outcome is in {@code resultCode}. */
    record PaymentResponse(String pspReference, String resultCode, String refusalReason) {
    }

    record ModificationResponse(String pspReference, String status) {
    }
}
