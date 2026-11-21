package com.designpatterns.level01.payments.application.commands;

import com.designpatterns.level01.payments.domain.MerchantId;

public final class UnknownMerchantException extends RuntimeException {

    public UnknownMerchantException(MerchantId merchantId) {
        super("Unknown merchant " + merchantId);
    }
}
