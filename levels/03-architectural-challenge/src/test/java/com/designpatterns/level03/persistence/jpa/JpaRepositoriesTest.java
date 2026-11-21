package com.designpatterns.level03.persistence.jpa;

import com.designpatterns.level03.model.Money;
import com.designpatterns.level03.model.Payment;
import com.designpatterns.level03.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** The JPA adapters against an embedded H2 database built from schema.sql. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@Import({JpaPaymentRepository.class, JpaIdempotencyRepository.class})
class JpaRepositoriesTest {

    @Autowired
    JpaPaymentRepository payments;
    @Autowired
    JpaIdempotencyRepository keys;

    @Test
    void aPaymentSurvivesTheRoundTrip() {
        Payment payment = Payment.create("pay_1", "acme", "cus_1", Money.of(1_000, "JPY"));
        payment.markCaptured("stripe", "pi_1");
        payment.recordRefund(Money.of(300, "JPY"));

        payments.save(payment);
        Payment loaded = payments.findById("pay_1").orElseThrow();

        assertThat(loaded.merchantId()).isEqualTo("acme");
        assertThat(loaded.amount()).isEqualTo(Money.of(1_000, "JPY"));
        assertThat(loaded.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(loaded.providerId()).isEqualTo("stripe");
        assertThat(loaded.providerReference()).isEqualTo("pi_1");
        assertThat(loaded.refunded()).isEqualTo(Money.of(300, "JPY"));
    }

    @Test
    void savingAgainUpdatesTheRow() {
        Payment payment = Payment.create("pay_2", "acme", "cus_1", Money.of(1_000, "EUR"));
        payments.save(payment);
        payment.markFailed("no provider");

        payments.save(payment);

        assertThat(payments.findById("pay_2").orElseThrow().status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void anUnknownPaymentIsEmpty() {
        assertThat(payments.findById("nope")).isEmpty();
    }

    @Test
    void anIdempotencyKeyRemembersItsResult() {
        keys.save("key-1", "pay_1");

        assertThat(keys.findResult("key-1")).contains("pay_1");
        assertThat(keys.findResult("key-2")).isEmpty();
    }
}
