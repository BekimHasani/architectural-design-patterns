package com.designpatterns.level09.integration.provider.adyen;

import com.designpatterns.level09.integration.gateway.FailureCategory;
import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.model.DeclineReason;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static com.designpatterns.level09.integration.provider.Fixtures.reply;
import static org.assertj.core.api.Assertions.assertThat;

/** Recorded Adyen responses → our model. No network, no simulator. */
class AdyenMapperTest {

    @Test
    void authorised() {
        assertThat(AdyenMapper.toChargeResult(reply(200, "adyen/authorised.json")))
                .isEqualTo(new GatewayResult.Authorized("NC6HT9CRT65ZGN82"));
    }

    @Test
    void refusedWithHttp200IsADecline() {
        assertThat(AdyenMapper.toChargeResult(reply(200, "adyen/refused_not_enough_balance.json")))
                .isEqualTo(new GatewayResult.Declined(DeclineReason.INSUFFICIENT_FUNDS));
    }

    @Test
    void redirectShopperRequiresAction() {
        assertThat(AdyenMapper.toChargeResult(reply(200, "adyen/redirect_shopper.json")))
                .isEqualTo(new GatewayResult.RequiresAction("QX7GD2KNX9WJ8K82",
                        URI.create("https://checkoutshopper-test.adyen.example/threeDS2/QX7G")));
    }

    @Test
    void validationErrorIsOurProblem() {
        assertThat(AdyenMapper.toChargeResult(reply(422, "adyen/error_validation.json")))
                .isInstanceOfSatisfying(GatewayResult.Failed.class,
                        f -> assertThat(f.category()).isEqualTo(FailureCategory.INVALID_REQUEST));
    }
}
