package com.designpatterns.level09.integration.provider.paypal;

import com.designpatterns.level09.integration.gateway.FailureCategory;
import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.model.DeclineReason;
import com.designpatterns.level09.model.Money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;

import static com.designpatterns.level09.integration.provider.Fixtures.reply;
import static org.assertj.core.api.Assertions.assertThat;

/** Recorded PayPal responses → our model. No network, no simulator. */
class PayPalMapperTest {

    @Test
    void createdOrderMeansTheBuyerMustApprove() {
        assertThat(PayPalMapper.toChargeResult(reply(201, "paypal/order_created.json")))
                .isEqualTo(new GatewayResult.RequiresAction("5O190127TN364715T",
                        URI.create("https://www.paypal.example/checkoutnow?token=5O190127TN364715T")));
    }

    @Test
    void completedOrderIsSucceeded() {
        assertThat(PayPalMapper.toChargeResult(reply(201, "paypal/order_completed.json")))
                .isEqualTo(new GatewayResult.Charged("5O190127TN364715T"));
    }

    @Test
    void approvedOrderCanBeCaptured() {
        assertThat(PayPalMapper.toChargeResult(reply(200, "paypal/order_approved.json")))
                .isEqualTo(new GatewayResult.Authorized("5O190127TN364715T"));
    }

    @Test
    void instrumentDeclinedIsADeclineEvenThoughItIsHttp422() {
        assertThat(PayPalMapper.toChargeResult(reply(422, "paypal/error_instrument_declined.json")))
                .isEqualTo(new GatewayResult.Declined(DeclineReason.GENERIC));
    }

    @Test
    void authenticationFailureIsOurProblem() {
        assertThat(PayPalMapper.toChargeResult(reply(401, "paypal/error_authentication.json")))
                .isInstanceOfSatisfying(GatewayResult.Failed.class,
                        f -> assertThat(f.category()).isEqualTo(FailureCategory.INVALID_REQUEST));
    }

    /** PayPal wants decimal strings. The number of decimals depends on the currency. */
    @ParameterizedTest
    @CsvSource({"4859, EUR, 48.59", "5, EUR, 0.05", "4859, JPY, 4859", "4859, KWD, 4.859"})
    void minorUnitsBecomeDecimalStrings(long minor, String currency, String expected) {
        assertThat(PayPalMapper.toDecimal(Money.of(minor, currency))).isEqualTo(expected);
    }
}
