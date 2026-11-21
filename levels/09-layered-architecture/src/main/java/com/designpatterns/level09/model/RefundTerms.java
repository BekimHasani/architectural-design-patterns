package com.designpatterns.level09.model;

public record RefundTerms(boolean partialAllowed, Integer maxRefunds) {

    public static final RefundTerms ANY = new RefundTerms(true, null);
}