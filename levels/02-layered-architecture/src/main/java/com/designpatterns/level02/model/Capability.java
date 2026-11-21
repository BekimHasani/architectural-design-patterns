package com.designpatterns.level02.model;

/**
 * What a provider can be asked to do, as a plain business word.
 *
 * <p>LAYERED CHANGE: in Level 1 every value also pointed at its interface ({@code Refunder.class}).
 * The model is the lowest layer and can't see the integration layer above it, so that link moved
 * up to {@code integration.gateway.CapabilityBinding}. Presentation ("refundsRequired"), service
 * (router, operations) and integration (descriptors) all share this one enum.
 */
public enum Capability {
    AUTHORIZE,
    CHARGE,
    CAPTURE,
    CANCEL,
    REFUND,
    CUSTOMER_ACTION
}
