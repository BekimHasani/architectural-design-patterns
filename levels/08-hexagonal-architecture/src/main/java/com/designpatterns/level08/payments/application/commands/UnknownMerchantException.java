package com.designpatterns.level08.payments.application.commands;

import com.designpatterns.level08.payments.domain.MerchantId;

public final class UnknownMerchantException extends RuntimeException {

    public UnknownMerchantException(MerchantId merchantId) {
        super("Unknown merchant " + merchantId);
    }
}
