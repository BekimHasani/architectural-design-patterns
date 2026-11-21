package com.designpatterns.level01.payments.domain;

/** What the merchant wants. It's business intent, and it doesn't adapt to the weakest provider. */
public enum CaptureMode {
    /** Hold the funds; the merchant captures or cancels later. */
    MANUAL,
    /** Take the money now, however the provider does it. */
    AUTOMATIC
}
