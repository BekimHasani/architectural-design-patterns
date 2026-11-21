package com.designpatterns.level02.service.payment;

import com.designpatterns.level02.integration.gateway.FailureCategory;
import com.designpatterns.level02.integration.gateway.GatewayResult;
import com.designpatterns.level02.model.AttemptOutcome;
import com.designpatterns.level02.model.DeclineReason;
import com.designpatterns.level02.model.MerchantId;
import com.designpatterns.level02.model.Money;
import com.designpatterns.level02.model.Payment;
import com.designpatterns.level02.model.PaymentId;
import com.designpatterns.level02.model.PaymentOperation;
import com.designpatterns.level02.model.PaymentStatus;
import com.designpatterns.level02.model.ProviderId;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/** "How does a provider's answer change a payment?": the one table every service relies on. */
class GatewayResultApplierTest {

    private static final ProviderId STRIPE = ProviderId.of("stripe");

    @Test
    void authorizationOutcomes() {
        assertThat(afterAuthorization(new GatewayResult.Authorized("pi"))).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(afterAuthorization(new GatewayResult.Charged("pi"))).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(afterAuthorization(new GatewayResult.Declined(DeclineReason.GENERIC))).isEqualTo(PaymentStatus.DECLINED);
        assertThat(afterAuthorization(new GatewayResult.RequiresAction("pi", URI.create("https://3ds"))))
                .isEqualTo(PaymentStatus.ACTION_REQUIRED);
        assertThat(afterAuthorization(new GatewayResult.Processing("pi"))).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void aTimeoutIsUnknown_neverATryTheNextProvider() {
        assertThat(afterAuthorization(failed(FailureCategory.OUTCOME_UNKNOWN))).isEqualTo(PaymentStatus.UNKNOWN);
    }

    @Test
    void onlyAFailureThatProvesNothingWasChargedFreesThePaymentForTheNextProvider() {
        assertThat(afterAuthorization(failed(FailureCategory.PROVIDER_UNAVAILABLE))).isEqualTo(PaymentStatus.CREATED);
        assertThat(afterAuthorization(failed(FailureCategory.INVALID_REQUEST))).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void operationOutcomes() {
        Payment payment = captured();
        PaymentOperation refund = payment.startRefund("op_1", Money.of(100, "EUR"));

        GatewayResultApplier.applyOperation(payment, refund.operationId(), failed(FailureCategory.OUTCOME_UNKNOWN));

        assertThat(payment.findOperation("op_1").orElseThrow().status()).isEqualTo(PaymentOperation.Status.UNKNOWN);
    }

    @Test
    void reconciliation_noRecordAtTheProviderMeansNothingWasCharged() {
        Payment payment = pending();
        payment.outcomeUnknown();

        assertThat(GatewayResultApplier.applyStatus(payment, failed(FailureCategory.NOT_PROCESSED))).isTrue();
        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void reconciliation_aFailedLookupSettlesNothing() {
        Payment payment = pending();
        payment.outcomeUnknown();

        assertThat(GatewayResultApplier.applyStatus(payment, failed(FailureCategory.PROVIDER_UNAVAILABLE))).isFalse();
        assertThat(payment.status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    @Test
    void attemptOutcomes() {
        assertThat(GatewayResultApplier.outcomeOf(failed(FailureCategory.RATE_LIMITED))).isEqualTo(AttemptOutcome.FAILED_NOTHING_CHARGED);
        assertThat(GatewayResultApplier.outcomeOf(failed(FailureCategory.OUTCOME_UNKNOWN))).isEqualTo(AttemptOutcome.OUTCOME_UNKNOWN);
        assertThat(GatewayResultApplier.outcomeOf(new GatewayResult.Charged("x"))).isEqualTo(AttemptOutcome.CAPTURED);
    }

    private static PaymentStatus afterAuthorization(GatewayResult result) {
        Payment payment = pending();
        GatewayResultApplier.applyAuthorization(payment, result, false);
        return payment.status();
    }

    private static Payment pending() {
        Payment payment = Payment.create(PaymentId.newId(), new MerchantId("acme"), "c-1", Money.of(1000, "EUR"));
        payment.startAttempt("att_1", STRIPE);
        return payment;
    }

    private static Payment captured() {
        Payment payment = pending();
        payment.charged("pi");
        return payment;
    }

    private static GatewayResult.Failed failed(FailureCategory category) {
        return new GatewayResult.Failed(category, "test");
    }
}
