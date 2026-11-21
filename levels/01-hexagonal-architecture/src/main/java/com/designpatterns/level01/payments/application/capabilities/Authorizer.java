package com.designpatterns.level01.payments.application.capabilities;

import com.designpatterns.level01.payments.application.ports.AuthorizeRequest;
import com.designpatterns.level01.payments.application.ports.GatewayResult;

/** Holds the funds; capture or cancel comes later. */
public interface Authorizer {

    GatewayResult authorize(AuthorizeRequest request);
}
