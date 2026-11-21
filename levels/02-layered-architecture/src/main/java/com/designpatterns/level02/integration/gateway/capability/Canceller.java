package com.designpatterns.level02.integration.gateway.capability;

import com.designpatterns.level02.integration.gateway.GatewayResult;
import com.designpatterns.level02.integration.gateway.OperationRequest;


/** Releases held funds. */
public interface Canceller {

    GatewayResult cancel(OperationRequest request);
}
