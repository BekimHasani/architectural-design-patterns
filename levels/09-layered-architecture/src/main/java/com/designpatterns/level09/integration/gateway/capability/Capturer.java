package com.designpatterns.level09.integration.gateway.capability;

import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.integration.gateway.OperationRequest;


/** Moves held funds. */
public interface Capturer {

    GatewayResult capture(OperationRequest request);
}
