package com.designpatterns.level09.integration.gateway.capability;

import com.designpatterns.level09.integration.gateway.AuthorizeRequest;
import com.designpatterns.level09.integration.gateway.GatewayResult;


/** Moves the money in one step (or answers Processing, for async-only providers). */
public interface DirectCharger {

    GatewayResult charge(AuthorizeRequest request);
}
