package com.designpatterns.level01.payments.application.capabilities;

import com.designpatterns.level01.payments.application.ports.OperationRequest;
import com.designpatterns.level01.payments.application.ports.GatewayResult;

/** Gives money back. */
public interface Refunder {

    GatewayResult refund(OperationRequest request);
}
