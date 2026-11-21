package com.designpatterns.level09.integration.gateway.capability;

import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.integration.gateway.ProviderCredentials;


/** Continues a payment after the customer acted (approval, 3-D Secure). */
public interface ActionCompleter {

    GatewayResult completeAction(String providerRef, ProviderCredentials credentials);
}
