package com.designpatterns.level03.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void addsAndSubtractsInMinorUnits() {
        assertThat(Money.of(1999, "EUR").plus(Money.of(1, "EUR"))).isEqualTo(Money.of(2000, "EUR"));
        assertThat(Money.of(2000, "EUR").minus(Money.of(500, "EUR"))).isEqualTo(Money.of(1500, "EUR"));
    }

    @Test
    void neverMixesCurrencies() {
        assertThatThrownBy(() -> Money.of(100, "EUR").plus(Money.of(100, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isNeverNegative() {
        assertThatThrownBy(() -> Money.of(-1, "EUR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(100, "EUR").minus(Money.of(101, "EUR"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownCurrencies() {
        assertThatThrownBy(() -> Money.of(100, "XQZ")).isInstanceOf(IllegalArgumentException.class);
    }
}
