package com.designpatterns.level08.payments.domain;

import java.util.Objects;

/** The tenant: every payment belongs to exactly one merchant. */
public record MerchantId(String value) {

    public MerchantId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value;
    }
}
