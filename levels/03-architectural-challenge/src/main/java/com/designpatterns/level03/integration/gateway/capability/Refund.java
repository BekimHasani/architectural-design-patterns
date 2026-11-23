package com.designpatterns.level03.integration.gateway.capability;

import com.designpatterns.level03.integration.gateway.GatewayResult;
import com.designpatterns.level03.integration.gateway.RefundRequest;

public interface Refund {
    GatewayResult refund(RefundRequest request);
}