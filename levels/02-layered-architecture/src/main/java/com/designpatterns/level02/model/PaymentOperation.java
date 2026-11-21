package com.designpatterns.level02.model;

import java.util.Objects;

public record PaymentOperation(String operationId, Type type, Money amount, Status status,
                               String providerRef, String reason) {

    public enum Type { CAPTURE, CANCEL, REFUND }
    public enum Status { IN_FLIGHT, SUCCEEDED, FAILED, UNKNOWN }

    public PaymentOperation {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
    }

    boolean counts(Type wanted) { return type == wanted && status != Status.FAILED; }
    boolean settled(Type wanted) { return type == wanted && status == Status.SUCCEEDED; }
    boolean open() { return status == Status.IN_FLIGHT || status == Status.UNKNOWN; }

    PaymentOperation with(Status newStatus, String ref, String why) {
        return new PaymentOperation(operationId, type, amount, newStatus, ref != null ? ref : providerRef, why);
    }
}
