package com.designpatterns.level08.payments.application.capabilities;

import com.designpatterns.level08.payments.application.ports.AuthorizeRequest;
import com.designpatterns.level08.payments.application.ports.GatewayResult;

/** Moves the money in one step (or answers Processing, for async-only providers). */
public interface DirectCharger {

    GatewayResult charge(AuthorizeRequest request);
}
