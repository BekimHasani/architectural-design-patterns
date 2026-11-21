package com.designpatterns.level08.payments.application.capabilities;

import com.designpatterns.level08.payments.application.ports.OperationRequest;
import com.designpatterns.level08.payments.application.ports.GatewayResult;

/** Moves held funds. */
public interface Capturer {

    GatewayResult capture(OperationRequest request);
}
