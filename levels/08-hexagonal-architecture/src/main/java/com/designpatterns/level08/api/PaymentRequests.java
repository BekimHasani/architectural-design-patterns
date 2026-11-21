package com.designpatterns.level08.api;

import com.designpatterns.level08.payments.application.capabilities.Capability;
import com.designpatterns.level08.payments.domain.CaptureMode;
import com.designpatterns.level08.payments.domain.Money;

import java.util.Set;

/** The public request bodies. */
final class PaymentRequests {

    private PaymentRequests() {
    }

    /**
     * @param capture          "manual" (default) or "automatic"
     * @param refundsRequired  only use a provider that can refund
     */
    record Authorize(String customerId, long amountMinor, String currency, String capture, boolean refundsRequired) {

        CaptureMode captureMode() {
            return capture == null ? CaptureMode.MANUAL : CaptureMode.valueOf(capture.toUpperCase());
        }

        Set<Capability> alsoRequired() {
            return refundsRequired ? Set.of(Capability.REFUND) : Set.of();
        }
    }

    /** {@code amountMinor} null = the full amount. */
    record Amount(Long amountMinor) {
    }

    static Money money(Amount request, String currency) {
        return request == null || request.amountMinor() == null ? null : Money.of(request.amountMinor(), currency);
    }
}
