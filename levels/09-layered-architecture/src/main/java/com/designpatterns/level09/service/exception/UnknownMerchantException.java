package com.designpatterns.level09.service.exception;

import com.designpatterns.level09.model.MerchantId;


public final class UnknownMerchantException extends RuntimeException {

    public UnknownMerchantException(MerchantId merchantId) {
        super("Unknown merchant " + merchantId);
    }
}
