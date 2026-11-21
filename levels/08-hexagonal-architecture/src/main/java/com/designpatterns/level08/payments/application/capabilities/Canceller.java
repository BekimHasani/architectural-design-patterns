package com.designpatterns.level08.payments.application.capabilities;

import com.designpatterns.level08.payments.application.ports.OperationRequest;
import com.designpatterns.level08.payments.application.ports.GatewayResult;

/** Releases held funds. */
public interface Canceller {

    GatewayResult cancel(OperationRequest request);
}
