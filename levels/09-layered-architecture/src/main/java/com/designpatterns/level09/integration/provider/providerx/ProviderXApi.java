package com.designpatterns.level09.integration.provider.providerx;

import java.util.List;

/** Provider X's JSON shapes. Package-private: they never leave this package. */
final class ProviderXApi {

    private ProviderXApi() {
    }

    record Charge(long cents, String ccy, String ref) {
    }

    record Refund(long cents, String ref) {
    }

    record ChargeResponse(@com.fasterxml.jackson.annotation.JsonProperty("charge_id") String chargeId, String state) {
    }

    record ChargeList(List<ChargeResponse> charges) {
    }
}
