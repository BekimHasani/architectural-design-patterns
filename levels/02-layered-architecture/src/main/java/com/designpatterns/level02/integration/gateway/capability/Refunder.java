package com.designpatterns.level02.integration.gateway.capability;

import com.designpatterns.level02.integration.gateway.GatewayResult;
import com.designpatterns.level02.integration.gateway.OperationRequest;


/** Gives money back. */
public interface Refunder {

    GatewayResult refund(OperationRequest request);
}
