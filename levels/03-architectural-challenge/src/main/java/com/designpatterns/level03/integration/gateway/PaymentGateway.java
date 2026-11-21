package com.designpatterns.level03.integration.gateway;

/** What the platform needs from any payment provider. One implementation per provider. */
public interface PaymentGateway {

    /** The provider id used in routing and stored on the payment, e.g. {@code "stripe"}. */
    String id();

    /** Charges immediately (authorize + capture in one step). */
    GatewayResult charge(ChargeRequest request);

    GatewayResult refund(RefundRequest request);
}
