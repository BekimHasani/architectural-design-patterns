package com.designpatterns.level09.model;

public enum ReportedOutcome {
    PROCESSING,
    /** Funds are held. */
    AUTHORIZED,
    /** Money moved: a direct charge. */
    CAPTURED,
    DECLINED
}