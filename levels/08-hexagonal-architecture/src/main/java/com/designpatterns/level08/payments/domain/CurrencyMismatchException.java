package com.designpatterns.level08.payments.domain;

import java.util.Currency;

public final class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(Currency left, Currency right) {
        super("Cannot combine " + left + " with " + right);
    }
}
