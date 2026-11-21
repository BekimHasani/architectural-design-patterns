package com.designpatterns.level02.integration.gateway.capability;

import com.designpatterns.level02.integration.gateway.GatewayResult;
import com.designpatterns.level02.integration.gateway.OperationRequest;


/** Moves held funds. */
public interface Capturer {

    GatewayResult capture(OperationRequest request);
}
