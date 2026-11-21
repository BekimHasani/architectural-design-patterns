package com.designpatterns.level08.payments.application.capabilities;

import com.designpatterns.level08.payments.application.ports.OperationRequest;
import com.designpatterns.level08.payments.application.ports.GatewayResult;

/** Gives money back. */
public interface Refunder {

    GatewayResult refund(OperationRequest request);
}
