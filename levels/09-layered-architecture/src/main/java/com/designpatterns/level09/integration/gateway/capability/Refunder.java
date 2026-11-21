package com.designpatterns.level09.integration.gateway.capability;

import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.integration.gateway.OperationRequest;


/** Gives money back. */
public interface Refunder {

    GatewayResult refund(OperationRequest request);
}
