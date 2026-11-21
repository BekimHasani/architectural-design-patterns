package com.designpatterns.level08.payments.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Names a payment provider ("stripe", "paypal", …) without knowing anything about it.
 * The router works only with these; it never sees an adapter.
 */
public record ProviderId(String value) {

    public ProviderId {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("ProviderId cannot be blank");
        }
    }

    public static ProviderId of(String value) {
        return new ProviderId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
