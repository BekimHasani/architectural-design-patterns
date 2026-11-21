package com.designpatterns.level03.service.idempotency;

import com.designpatterns.level03.support.InMemoryIdempotencyRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyServiceTest {

    private final IdempotencyService service = new IdempotencyService(new InMemoryIdempotencyRepository());
    private final AtomicInteger runs = new AtomicInteger();

    private String charge(String paymentId) {
        runs.incrementAndGet();
        return paymentId;
    }

    @Test
    void theFirstRequestRunsTheCommand() {
        String result = service.execute("acme", "POST /payments", "key-1", () -> charge("pay_1"));

        assertThat(result).isEqualTo("pay_1");
        assertThat(runs).hasValue(1);
    }

    @Test
    void aRetryWithTheSameKeyGetsTheFirstResultWithoutRunningAgain() {
        service.execute("acme", "POST /payments", "key-1", () -> charge("pay_1"));

        String retry = service.execute("acme", "POST /payments", "key-1", () -> charge("pay_2"));

        assertThat(retry).isEqualTo("pay_1");
        assertThat(runs).hasValue(1);
    }

    @Test
    void differentKeysAreDifferentRequests() {
        service.execute("acme", "POST /payments", "key-1", () -> charge("pay_1"));
        String second = service.execute("acme", "POST /payments", "key-2", () -> charge("pay_2"));

        assertThat(second).isEqualTo("pay_2");
        assertThat(runs).hasValue(2);
    }

    @Test
    void aKeyIsRequired() {
        assertThatThrownBy(() -> service.execute("acme", "POST /payments", " ", () -> charge("pay_1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(runs).hasValue(0);
    }

    @Test
    @Tag("challenge")
    void theSameKeyFromAnotherMerchantIsADifferentRequest() {
        service.execute("acme", "POST /payments", "order-42", () -> charge("pay_acme"));

        String globex = service.execute("globex", "POST /payments", "order-42", () -> charge("pay_globex"));

        assertThat(globex).as("globex must never receive acme's payment").isEqualTo("pay_globex");
        assertThat(runs).hasValue(2);
    }

    @Test
    @Tag("challenge")
    void theSameKeyOnAnotherOperationIsADifferentRequest() {
        service.execute("acme", "POST /payments", "key-1", () -> charge("pay_1"));

        String refund = service.execute("acme", "POST /payments/pay_1/refunds", "key-1", () -> charge("pay_1-refunded"));

        assertThat(refund).isEqualTo("pay_1-refunded");
        assertThat(runs).hasValue(2);
    }
}
