package com.designpatterns.level02.integration.gateway.capability;

import com.designpatterns.level02.integration.gateway.AuthorizeRequest;
import com.designpatterns.level02.integration.gateway.GatewayResult;


/** Holds the funds; capture or cancel comes later. */
public interface Authorizer {

    GatewayResult authorize(AuthorizeRequest request);
}
