package com.designpatterns.level02.model;

import java.util.Objects;

public record MerchantId(String value) {
    public MerchantId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString(){
        return value;
    }
}
