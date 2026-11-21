package com.designpatterns.level01.payments.domain;

import java.util.Currency;
import java.util.Objects;

/**
 * A value object: an amount in integer minor units (cents) plus its currency.
 * Never use double for money: 0.1 + 0.2 != 0.3.
 */
public record Money(long amountMinor, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (amountMinor < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + amountMinor);
        }
    }

    public static Money of(long amountMinor, String currencyCode) {
        return new Money(amountMinor, Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money times(int factor) {
        return new Money(Math.multiplyExact(amountMinor, factor), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amountMinor > other.amountMinor;
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}
