package com.designpatterns.level09.integration.gateway;


/**
 * The ONLY thing every provider must do: say what it can do, and answer "what happened to attempt
 * X?" (reconciliation can't work without it). Everything else is an optional capability interface.
 */
public interface ProviderAdapter {

    ProviderDescriptor descriptor();

    /** {@code Failed(NOT_PROCESSED)} if the provider never saw the attempt. */
    GatewayResult getStatus(String attemptId, ProviderCredentials credentials);
}
