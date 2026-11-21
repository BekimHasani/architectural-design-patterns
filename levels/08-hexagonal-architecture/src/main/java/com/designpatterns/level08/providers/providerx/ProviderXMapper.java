package com.designpatterns.level08.providers.providerx;

import com.designpatterns.level08.payments.application.ports.GatewayResult;
import com.designpatterns.level08.payments.domain.DeclineReason;
import com.designpatterns.level08.providers.support.ProviderHttp;
import com.designpatterns.level08.providers.support.ProviderHttp.Reply;

/** Provider X ⇄ our model. "paid" means the money moved: this provider has no authorization step. */
final class ProviderXMapper {

    private ProviderXMapper() {
    }

    static GatewayResult toChargeResult(Reply reply) {
        if (reply.status() != 200) {
            return ProviderHttp.failure(reply);
        }
        return ProviderHttp.parse(reply.body(), ProviderXApi.ChargeResponse.class)
                .map(ProviderXMapper::fromCharge)
                .orElseGet(() -> ProviderHttp.unreadable(reply));
    }

    static GatewayResult toStatusResult(Reply reply) {
        if (reply.status() != 200) {
            return ProviderHttp.failure(reply);
        }
        return ProviderHttp.parse(reply.body(), ProviderXApi.ChargeList.class)
                .map(found -> found.charges() == null || found.charges().isEmpty()
                        ? ProviderHttp.notProcessed()
                        : fromCharge(found.charges().get(0)))
                .orElseGet(() -> ProviderHttp.unreadable(reply));
    }

    static GatewayResult toRefundResult(Reply reply, String chargeId) {
        return reply.status() == 200 ? new GatewayResult.Done(chargeId) : ProviderHttp.failure(reply);
    }

    private static GatewayResult fromCharge(ProviderXApi.ChargeResponse charge) {
        return "paid".equals(charge.state())
                ? new GatewayResult.Charged(charge.chargeId())
                : new GatewayResult.Declined(DeclineReason.GENERIC);
    }
}
