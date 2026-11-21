package com.designpatterns.level09.presentation.rest.dto;

import com.designpatterns.level09.model.CaptureMode;
import com.designpatterns.level09.model.Capability;

import java.util.Set;

/**
 * DTO: the public JSON shape of {@code POST /payments}. It is NOT the model: the API can stay stable
 * while the model changes, and the model never gets JSON annotations.
 *
 * @param capture         "manual" (default) or "automatic"
 * @param refundsRequired only use a provider that can refund
 */
public record AuthorizeRequestDto(String customerId, long amountMinor, String currency, String capture,
                                  boolean refundsRequired) {

    public CaptureMode captureMode() {
        return capture == null ? CaptureMode.MANUAL : CaptureMode.valueOf(capture.toUpperCase());
    }

    public Set<Capability> alsoRequired() {
        return refundsRequired ? Set.of(Capability.REFUND) : Set.of();
    }
}
