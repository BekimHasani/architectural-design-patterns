package com.designpatterns.level01.payments.application.routing;

import com.designpatterns.level01.payments.application.capabilities.Capability;
import com.designpatterns.level01.payments.domain.CaptureMode;

import java.util.Currency;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What the payment needs. Rules look at currency and merchant; the router also filters on what
 * the business intends to do with the payment.
 *
 * @param alsoRequired capabilities the caller insists on, e.g. REFUND
 */
public record RoutingContext(Currency currency, Optional<String> merchantId, CaptureMode captureMode,
                             Set<Capability> alsoRequired) {

    public RoutingContext {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(captureMode, "captureMode");
        alsoRequired = Set.copyOf(alsoRequired);
    }

    public RoutingContext(Currency currency, Optional<String> merchantId) {
        this(currency, merchantId, CaptureMode.AUTOMATIC, Set.of());
    }
}
