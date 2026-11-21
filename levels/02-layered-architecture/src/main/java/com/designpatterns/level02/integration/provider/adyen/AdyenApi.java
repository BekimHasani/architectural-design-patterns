package com.designpatterns.level02.integration.provider.adyen;

import java.util.List;

/** Adyen's JSON shapes. Package-private: they never leave this package. */
final class AdyenApi {

    private AdyenApi() {
    }

    /** {@code manualCapture}: hold the funds; money moves only at /captures. */
    record PaymentRequest(Amount amount, String reference, String merchantAccount, boolean manualCapture) {
    }

    record Amount(long value, String currency) {
    }

    record ModificationRequest(Amount amount, String merchantAccount, String reference) {
    }

    record DetailsRequest(String pspReference) {
    }

    /** Arrives with HTTP 200 for Authorised AND for Refused: the outcome is in {@code resultCode}. */
    record PaymentResponse(String pspReference, String resultCode, String refusalReason, Action action) {
    }

    record PaymentList(List<PaymentResponse> payments) {
    }

    record Action(String type, String url) {
    }
}
