package com.designpatterns.level09.integration.gateway.capability;

import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.integration.gateway.OperationRequest;


/** Releases held funds. */
public interface Canceller {

    GatewayResult cancel(OperationRequest request);
}
