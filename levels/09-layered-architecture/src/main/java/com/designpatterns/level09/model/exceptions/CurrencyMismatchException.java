package com.designpatterns.level09.model.exceptions;

import java.util.Currency;

public final class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(Currency left, Currency right) {
        super("Cannot combine " + left + " with " + right);
    }
}
