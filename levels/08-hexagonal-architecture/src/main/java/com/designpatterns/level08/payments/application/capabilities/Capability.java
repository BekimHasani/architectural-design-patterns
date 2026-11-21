package com.designpatterns.level08.payments.application.capabilities;

/** What a provider can do. Each value IS an interface, so a declaration can be checked against the code. */
public enum Capability {
    AUTHORIZE(Authorizer.class),
    CHARGE(DirectCharger.class),
    CAPTURE(Capturer.class),
    CANCEL(Canceller.class),
    REFUND(Refunder.class),
    CUSTOMER_ACTION(ActionCompleter.class);

    private final Class<?> type;

    Capability(Class<?> type) {
        this.type = type;
    }

    public Class<?> type() {
        return type;
    }
}
