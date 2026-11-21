package com.designpatterns.level02.integration.gateway;

import com.designpatterns.level02.model.DeclineReason;


import java.net.URI;

/** Every outcome of a provider call, in OUR words. */
public sealed interface GatewayResult {

    /** Funds are held; capture or cancel next. */
    record Authorized(String providerRef) implements GatewayResult {
    }

    /** Money moved in one step (direct-charge providers, or a capture seen through a status lookup). */
    record Charged(String providerRef) implements GatewayResult {
    }

    /** A capture, cancel or refund was done. {@code providerRef} is what later operations must point at. */
    record Done(String providerRef) implements GatewayResult {
    }

    record Declined(DeclineReason reason) implements GatewayResult {
    }

    record RequiresAction(String providerRef, URI redirectUrl) implements GatewayResult {
    }

    record Processing(String providerRef) implements GatewayResult {
    }

    /** {@code detail} is for logs, never for the customer. */
    record Failed(FailureCategory category, String detail) implements GatewayResult {
    }
}
