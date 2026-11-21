package com.designpatterns.level02.model;

import com.designpatterns.level02.model.Money;
import com.designpatterns.level02.model.exceptions.CurrencyMismatchException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MoneyTest {

    @Test
    void addsSameCurrency() {
        assertThat(Money.of(1999, "EUR").plus(Money.of(1, "EUR"))).isEqualTo(Money.of(2000, "EUR"));
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.of(100, "EUR").plus(Money.of(100, "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void cannotBeNegative() {
        assertThatThrownBy(() -> Money.of(-1, "EUR")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesAmountsOfTheSameCurrencyOnly() {
        assertThat(Money.of(101, "EUR").isGreaterThan(Money.of(100, "EUR"))).isTrue();
        assertThatThrownBy(() -> Money.of(1, "EUR").isGreaterThan(Money.of(1, "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
    }
}
