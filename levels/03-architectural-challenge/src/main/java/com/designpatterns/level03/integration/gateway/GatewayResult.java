package com.designpatterns.level03.integration.gateway;

/** Every answer a provider can give, in OUR words. Adapters never throw for a provider problem. */
public sealed interface GatewayResult {

    /** Done. {@code providerReference} is the provider's id of the charge or refund. */
    record Succeeded(String providerReference) implements GatewayResult {
    }

    /** The provider said no (card declined, insufficient funds, ...). Nothing was charged. */
    record Declined(String reason) implements GatewayResult {
    }

    record Failed(FailureKind kind, String detail) implements GatewayResult {
    }

    enum FailureKind {
        /** The request provably never reached the provider, or the provider rejected it before processing. */
        NOTHING_CHARGED,
        /** The request was sent and no usable answer came back: the provider MAY have processed it. */
        OUTCOME_UNKNOWN
    }
}
