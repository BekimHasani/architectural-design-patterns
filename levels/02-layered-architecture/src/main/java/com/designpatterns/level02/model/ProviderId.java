package com.designpatterns.level02.model;

import java.util.Locale;
import java.util.Objects;

public record ProviderId (String value){
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
