package com.designpatterns.level09.integration.provider.stripe;

import com.designpatterns.level09.integration.gateway.AuthorizeRequest;
import com.designpatterns.level09.integration.gateway.FailureCategory;
import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.integration.gateway.ProviderCredentials;
import com.designpatterns.level09.model.DeclineReason;
import com.designpatterns.level09.model.Money;
import com.designpatterns.level09.model.PaymentId;

import com.designpatterns.level09.integration.support.ProviderHttp.Reply;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static com.designpatterns.level09.integration.provider.Fixtures.reply;
import static org.assertj.core.api.Assertions.assertThat;

/** Recorded Stripe responses → our model. No network, no simulator. */
class StripeMapperTest {

    @Test
    void succeeded() {
        assertThat(StripeMapper.toChargeResult(reply(200, "stripe/payment_intent_succeeded.json")))
                .isEqualTo(new GatewayResult.Charged("pi_3Q"));
    }

    @Test
    void requiresActionCarriesTheRedirect() {
        assertThat(StripeMapper.toChargeResult(reply(200, "stripe/payment_intent_requires_action.json")))
                .isEqualTo(new GatewayResult.RequiresAction("pi_3R", URI.create("https://hooks.stripe.example/3ds/pi_3R")));
    }

    @Test
    void declineCodeBecomesOurReason() {
        assertThat(StripeMapper.toChargeResult(reply(402, "stripe/error_insufficient_funds.json")))
                .isEqualTo(new GatewayResult.Declined(DeclineReason.INSUFFICIENT_FUNDS));
    }

    @Test
    void invalidApiKeyIsOurProblem() {
        assertThat(StripeMapper.toChargeResult(reply(401, "stripe/error_invalid_api_key.json")))
                .isInstanceOfSatisfying(GatewayResult.Failed.class,
                        f -> assertThat(f.category()).isEqualTo(FailureCategory.INVALID_REQUEST));
    }

    @Test
    void garbageIsUnknown() {
        assertThat(StripeMapper.toChargeResult(new Reply(200, "<html>Bad Gateway</html>")))
                .isInstanceOfSatisfying(GatewayResult.Failed.class,
                        f -> assertThat(f.category()).isEqualTo(FailureCategory.OUTCOME_UNKNOWN));
    }

    @Test
    void requestUsesMinorUnitsAndLowerCaseCurrency() {
        StripeApi.CreatePaymentIntent request = StripeMapper.toRequest(
                new AuthorizeRequest(PaymentId.newId(), "c-1", Money.of(4859, "EUR"), "att_1", ProviderCredentials.of("api-key", "k")));

        assertThat(request).isEqualTo(new StripeApi.CreatePaymentIntent(4859, "eur", true, "manual", Map.of("attempt_id", "att_1")));
    }

    @Test
    void searchWithoutHitsMeansNotProcessed() {
        assertThat(StripeMapper.toStatusResult(new Reply(200, "{\"object\":\"search_result\",\"data\":[]}")))
                .isInstanceOfSatisfying(GatewayResult.Failed.class,
                        f -> assertThat(f.category()).isEqualTo(FailureCategory.NOT_PROCESSED));
    }

    @Test
    void searchHitIsMappedLikeAnyPaymentIntent() {
        assertThat(StripeMapper.toStatusResult(new Reply(200,
                "{\"data\":[{\"id\":\"pi_7\",\"status\":\"succeeded\"}]}")))
                .isEqualTo(new GatewayResult.Charged("pi_7"));
    }
}
