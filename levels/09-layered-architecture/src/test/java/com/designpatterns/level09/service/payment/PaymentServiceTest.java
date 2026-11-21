package com.designpatterns.level09.service.payment;

import com.designpatterns.level09.integration.gateway.FailureCategory;
import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.model.AttemptOutcome;
import com.designpatterns.level09.model.MerchantId;
import com.designpatterns.level09.model.Money;
import com.designpatterns.level09.model.Payment;
import com.designpatterns.level09.model.PaymentAttempt;
import com.designpatterns.level09.model.PaymentStatus;
import com.designpatterns.level09.service.exception.UnknownMerchantException;
import com.designpatterns.level09.support.ScriptedGateway;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentServiceTest extends PlatformFixture {

    private static final Money AMOUNT = Money.of(10000, "EUR");

    @Test
    void storesFirst_thenCallsTheProvider_withTheMerchantsOwnCredentials() {
        AtomicReference<PaymentStatus> seen = new AtomicReference<>();
        ScriptedGateway first = new ScriptedGateway(new GatewayResult.Authorized("ref"))
                .duringAuthorize(r -> seen.set(payments.find(r.paymentId()).orElseThrow().status()));

        PaymentResult result = authorizePayment(first, new ScriptedGateway()).authorize(ACME, "c-1", AMOUNT);

        assertThat(seen.get()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(first.authorizations.get(0).credentials().require("api-key")).isEqualTo("first-acme");
        assertThat(first.authorizations.get(0).attemptId()).isEqualTo(result.payment().attemptId());
    }

    @Test
    void differentMerchants_differentCredentials_sameGatewayInstance() {
        ScriptedGateway first = new ScriptedGateway(new GatewayResult.Authorized("a"), new GatewayResult.Authorized("b"));
        PaymentService authorize = authorizePayment(first, new ScriptedGateway());

        authorize.authorize(ACME, "c-1", AMOUNT);
        authorize.authorize(GLOBEX, "c-2", AMOUNT);

        assertThat(first.authorizations).extracting(r -> r.credentials().require("api-key"))
                .containsExactly("first-acme", "first-globex");
    }

    @Test
    void fallsBackOnlyWhenNothingWasCharged() {
        ScriptedGateway second = new ScriptedGateway(new GatewayResult.Authorized("ref-2"));

        PaymentResult result = authorizePayment(
                new ScriptedGateway(new GatewayResult.Failed(FailureCategory.PROVIDER_UNAVAILABLE, "503")), second)
                .authorize(ACME, "c-1", AMOUNT);

        assertThat(result.payment().providerId()).isEqualTo(SECOND);
        assertThat(result.attempts()).extracting(PaymentAttempt::outcome)
                .containsExactly(AttemptOutcome.FAILED_NOTHING_CHARGED, AttemptOutcome.AUTHORIZED);
    }

    @Test
    void unknownOutcomeNeverFallsBack() {
        ScriptedGateway second = new ScriptedGateway(new GatewayResult.Authorized("ref-2"));

        Payment payment = authorizePayment(
                new ScriptedGateway(new GatewayResult.Failed(FailureCategory.OUTCOME_UNKNOWN, "timeout")), second)
                .authorize(ACME, "c-1", AMOUNT).payment();

        assertThat(payment.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(second.authorizations).isEmpty();
    }

    @Test
    void aProviderTheMerchantHasNoContractWithIsNotACandidate() {
        ScriptedGateway second = new ScriptedGateway(new GatewayResult.Authorized("never"));

        PaymentResult result = authorizePayment(
                new ScriptedGateway(new GatewayResult.Failed(FailureCategory.PROVIDER_UNAVAILABLE, "503")), second)
                .authorize(GLOBEX, "c-1", AMOUNT); // globex only has "first"

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(second.authorizations).isEmpty();
    }

    @Test
    void unknownMerchantIsRejectedBeforeAnythingIsStored() {
        assertThatThrownBy(() -> authorizePayment(new ScriptedGateway(), new ScriptedGateway())
                .authorize(new MerchantId("nobody"), "c-1", AMOUNT)).isInstanceOf(UnknownMerchantException.class);
        assertThat(payments.savedStatuses).isEmpty();
    }
}
