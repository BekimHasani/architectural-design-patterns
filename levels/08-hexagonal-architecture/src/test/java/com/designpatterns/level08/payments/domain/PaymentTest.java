package com.designpatterns.level08.payments.domain;

import com.designpatterns.level08.payments.domain.PaymentOperation.Status;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The money rules, in milliseconds: no database, no provider, no Spring. */
class PaymentTest {

    private static final ProviderId STRIPE = ProviderId.of("stripe");

    @Test
    void authorize100_capture60_refund30_then30MoreEmptiesIt() {
        Payment p = authorized(10000);

        p.operationSucceeded(p.startCapture("op_c", eur(6000)).operationId(), "ref");
        assertThat(p.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(p.captured()).isEqualTo(eur(6000));

        p.operationSucceeded(p.startRefund("op_r1", eur(3000)).operationId(), "ref");
        assertThat(p.refunded()).isEqualTo(eur(3000));
        assertThat(p.refundable()).isEqualTo(eur(3000));
        assertThat(p.status()).isEqualTo(PaymentStatus.CAPTURED);

        assertThatThrownBy(() -> p.startRefund("op_r2", eur(3001))).isInstanceOf(PaymentRuleViolationException.class);

        p.operationSucceeded(p.startRefund("op_r3", eur(3000)).operationId(), "ref");
        assertThat(p.status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void cannotCaptureMoreThanAuthorized() {
        assertThatThrownBy(() -> authorized(10000).startCapture("op", eur(10001)))
                .isInstanceOf(PaymentRuleViolationException.class);
    }

    @Test
    void anInFlightRefundReservesItsAmount() {
        Payment p = captured(6000);
        p.startRefund("op_1", eur(6000));

        assertThat(p.refundable().isZero()).isTrue();
        assertThatThrownBy(() -> p.startRefund("op_2", eur(1))).isInstanceOf(PaymentRuleViolationException.class);
    }

    @Test
    void aFailedRefundReleasesTheReservation_anUnknownOneKeepsIt() {
        Payment failed = captured(6000);
        failed.startRefund("op_1", eur(6000));
        failed.operationFailed("op_1", "declined");
        assertThat(failed.refundable()).isEqualTo(eur(6000));

        Payment unknown = captured(6000);
        unknown.startRefund("op_1", eur(6000));
        unknown.operationUnknown("op_1");
        assertThat(unknown.refundable().isZero()).as("the money may have moved").isTrue();
        assertThat(unknown.findOperation("op_1").orElseThrow().status()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void cancelAndCaptureCannotOverlap() {
        Payment p = authorized(10000);
        p.startCapture("op_c", eur(10000));

        assertThatThrownBy(() -> p.startCancel("op_x")).isInstanceOf(PaymentRuleViolationException.class);
        assertThatThrownBy(() -> p.startCapture("op_y", eur(1))).isInstanceOf(PaymentRuleViolationException.class);
    }

    @Test
    void cancelEndsTheAuthorization() {
        Payment p = authorized(10000);
        p.operationSucceeded(p.startCancel("op").operationId(), "ref");

        assertThat(p.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThatThrownBy(() -> p.startCapture("op2", null)).isInstanceOf(IllegalPaymentTransitionException.class);
    }

    @Test
    void capturedPaymentCannotBeCancelled_onlyRefunded() {
        assertThatThrownBy(() -> captured(100).startCancel("op")).isInstanceOf(IllegalPaymentTransitionException.class);
        assertThatThrownBy(() -> authorized(100).startRefund("op", null)).isInstanceOf(IllegalPaymentTransitionException.class);
    }

    @Test
    void aDirectChargeIsAuthorizedAndCapturedInOneStep() {
        Payment p = pending(10000);
        p.charged("x_1");

        assertThat(p.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(p.captured()).isEqualTo(eur(10000));
        assertThat(p.refundable()).isEqualTo(eur(10000));
        assertThat(p.refundTarget()).isEqualTo("x_1");
    }

    @Test
    void refundPointsAtTheCaptureNotTheAuthorization() {
        Payment p = authorized(100); // PayPal: the order id is not what you refund
        p.operationSucceeded(p.startCapture("op", null).operationId(), "CAP1");

        assertThat(p.refundTarget()).isEqualTo("CAP1");
        assertThat(p.providerId()).as("the domain remembers who took the money").isEqualTo(STRIPE);
    }

    @Test
    void everyStateChangeLeavesAnEventWithAnActor() {
        Payment p = Payment.create(PaymentId.newId(), new MerchantId("acme"), "c-1", eur(100));
        p.startAttempt("att_1", STRIPE);
        p.actingAs("provider:stripe").authorized("pi_1");

        assertThat(p.newEvents()).extracting(PaymentEvent::type)
                .containsExactly("PaymentCreated", "AttemptStarted", "PaymentAuthorized");
        assertThat(p.newEvents().get(2).actor()).isEqualTo("provider:stripe");
        assertThat(p.newEvents().get(2).from()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void providerNewsInAnyOrder() {
        Payment p = pending(100);
        assertThat(p.report(ReportedOutcome.PROCESSING, "pi")).isEqualTo(ReportResult.APPLIED);
        assertThat(p.report(ReportedOutcome.AUTHORIZED, "pi")).isEqualTo(ReportResult.APPLIED);
        assertThat(p.report(ReportedOutcome.AUTHORIZED, "pi")).isEqualTo(ReportResult.ALREADY_KNOWN);
        assertThat(p.report(ReportedOutcome.PROCESSING, "pi")).isEqualTo(ReportResult.STALE);
        assertThat(p.report(ReportedOutcome.DECLINED, "pi")).isEqualTo(ReportResult.CONFLICT);
        assertThat(p.status()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    void unknownBlocksEveryNewAttempt() {
        Payment p = pending(100);
        p.outcomeUnknown();

        assertThatThrownBy(() -> p.startAttempt("att_2", STRIPE)).isInstanceOf(IllegalPaymentTransitionException.class);
        assertThatThrownBy(p::nothingCharged).isInstanceOf(IllegalPaymentTransitionException.class);
    }

    @Test
    void customerActionThenAuthorized() {
        Payment p = pending(100);
        p.requiresAction("PP1", URI.create("https://provider.example/approve"));
        p.startCompletion("att_2");
        p.authorized("PP1");

        assertThat(p.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(p.redirectUrl()).isNull();
    }

    @Test
    void aProvidersRefundTermsAreEnforcedByTheAggregate() {
        RefundTerms onceInFull = new RefundTerms(false, 1);
        Payment p = captured(6000);

        assertThatThrownBy(() -> p.startRefund("op_1", eur(1000), onceInFull)).isInstanceOf(PaymentRuleViolationException.class);

        Payment twice = captured(6000);
        RefundTerms once = new RefundTerms(true, 1);
        twice.operationSucceeded(twice.startRefund("op_1", eur(1000), once).operationId(), "ref");
        assertThatThrownBy(() -> twice.startRefund("op_2", eur(1000), once)).isInstanceOf(PaymentRuleViolationException.class);
    }

    private static Money eur(long minor) {
        return Money.of(minor, "EUR");
    }

    private static Payment pending(long amount) {
        Payment p = Payment.create(PaymentId.newId(), new MerchantId("acme"), "c-1", eur(amount));
        p.startAttempt("att_1", STRIPE);
        return p;
    }

    private static Payment authorized(long amount) {
        Payment p = pending(amount);
        p.authorized("pi_1");
        return p;
    }

    private static Payment captured(long amount) {
        Payment p = authorized(amount);
        p.operationSucceeded(p.startCapture("op_cap", null).operationId(), "pi_1");
        return p;
    }
}
