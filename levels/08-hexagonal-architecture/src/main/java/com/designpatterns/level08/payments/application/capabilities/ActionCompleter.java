package com.designpatterns.level08.payments.application.capabilities;

import com.designpatterns.level08.payments.application.ports.GatewayResult;
import com.designpatterns.level08.payments.application.ports.ProviderCredentials;

/** Continues a payment after the customer acted (approval, 3-D Secure). */
public interface ActionCompleter {

    GatewayResult completeAction(String providerRef, ProviderCredentials credentials);
}
