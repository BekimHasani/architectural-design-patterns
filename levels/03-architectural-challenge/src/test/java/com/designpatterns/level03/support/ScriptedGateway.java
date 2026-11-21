package com.designpatterns.level03.support;

import com.designpatterns.level03.integration.gateway.ChargeRequest;
import com.designpatterns.level03.integration.gateway.GatewayResult;
import com.designpatterns.level03.integration.gateway.PaymentGateway;
import com.designpatterns.level03.integration.gateway.RefundRequest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Test double: answers charges from a script and records every call. No HTTP involved. */
public final class ScriptedGateway implements PaymentGateway {

    public final List<ChargeRequest> charges = new ArrayList<>();
    public final List<RefundRequest> refunds = new ArrayList<>();
    private final String id;
    private final Deque<GatewayResult> chargeScript;
    private GatewayResult refundResult = new GatewayResult.Succeeded("re_scripted");

    public ScriptedGateway(String id, GatewayResult... chargeResults) {
        this.id = id;
        this.chargeScript = new ArrayDeque<>(List.of(chargeResults));
    }

    public ScriptedGateway refundsWith(GatewayResult result) {
        this.refundResult = result;
        return this;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public GatewayResult charge(ChargeRequest request) {
        charges.add(request);
        if (chargeScript.isEmpty()) {
            throw new AssertionError(id + " was charged more often than the test scripted");
        }
        return chargeScript.poll();
    }

    @Override
    public GatewayResult refund(RefundRequest request) {
        refunds.add(request);
        return refundResult;
    }
}
