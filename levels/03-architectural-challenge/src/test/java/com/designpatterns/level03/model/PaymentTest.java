package com.designpatterns.level03.model;

import com.designpatterns.level03.model.exceptions.PaymentRuleViolationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static Payment captured(long amountMinor) {
        Payment payment = Payment.create("pay_1", "acme", "cus_1", Money.of(amountMinor, "EUR"));
        payment.markCaptured("stripe", "pi_1");
        return payment;
    }

    @Test
    void startsAsCreatedWithNothingRefunded() {
        Payment payment = Payment.create("pay_1", "acme", "cus_1", Money.of(1999, "EUR"));

        assertThat(payment.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payment.refunded()).isEqualTo(Money.of(0, "EUR"));
    }

    @Test
    void aPaymentNeedsAnAmount() {
        assertThatThrownBy(() -> Payment.create("pay_1", "acme", "cus_1", Money.of(0, "EUR")))
                .isInstanceOf(PaymentRuleViolationException.class);
    }

    @Test
    void aCapturedPaymentCannotBeCapturedAgain() {
        Payment payment = captured(1999);

        assertThatThrownBy(() -> payment.markCaptured("adyen", "psp_1")).isInstanceOf(PaymentRuleViolationException.class);
    }

    @Test
    void aPartialRefundLeavesItPartiallyRefunded() {
        Payment payment = captured(10_000);

        payment.recordRefund(Money.of(2_500, "EUR"));

        assertThat(payment.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.refunded()).isEqualTo(Money.of(2_500, "EUR"));
    }

    @Test
    void refundingTheRestMakesItRefunded() {
        Payment payment = captured(10_000);

        payment.recordRefund(Money.of(6_000, "EUR"));
        payment.recordRefund(Money.of(4_000, "EUR"));

        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void aSingleRefundCannotExceedTheAmount() {
        Payment payment = captured(10_000);

        assertThatThrownBy(() -> payment.checkRefundable(Money.of(10_001, "EUR")))
                .isInstanceOf(PaymentRuleViolationException.class);
    }

    @Test
    void onlyCapturedPaymentsCanBeRefunded() {
        Payment declined = Payment.create("pay_1", "acme", "cus_1", Money.of(1999, "EUR"));
        declined.markDeclined("stripe", "card_declined");

        assertThatThrownBy(() -> declined.checkRefundable(Money.of(100, "EUR")))
                .isInstanceOf(PaymentRuleViolationException.class);
    }

    @Test
    void aRefundMustBeInThePaymentsCurrency() {
        Payment payment = captured(10_000);

        assertThatThrownBy(() -> payment.checkRefundable(Money.of(100, "USD"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("challenge")
    void refundsTogetherCanNeverExceedTheCapturedAmount() {
        Payment payment = captured(10_000);
        payment.recordRefund(Money.of(6_000, "EUR"));

        assertThatThrownBy(() -> payment.checkRefundable(Money.of(6_000, "EUR")))
                .as("only 40.00 EUR is left to refund")
                .isInstanceOf(PaymentRuleViolationException.class);
        assertThat(payment.refunded()).isEqualTo(Money.of(6_000, "EUR"));
    }
}
