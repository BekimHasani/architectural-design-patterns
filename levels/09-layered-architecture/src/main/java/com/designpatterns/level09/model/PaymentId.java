package com.designpatterns.level09.model;

import java.util.Objects;
import java.util.UUID;

public record PaymentId(String value) {

    public PaymentId {
        Objects.requireNonNull(value, "value");
    }

    public static PaymentId newId(){
        return new PaymentId("pay_" + UUID.randomUUID());
    }

    @Override
    public String toString() { return value; }
}
