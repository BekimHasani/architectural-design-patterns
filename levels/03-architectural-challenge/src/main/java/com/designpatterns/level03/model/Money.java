package com.designpatterns.level03.model;

import java.util.Currency;
import java.util.Objects;

/** An amount in integer MINOR units (cents) plus its currency. Never a double. */
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

    /** Throws if the result would be negative. */
    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amountMinor - other.amountMinor, currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amountMinor > other.amountMinor;
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    public void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return amountMinor + " " + currency.getCurrencyCode() + " (minor units)";
    }
}
