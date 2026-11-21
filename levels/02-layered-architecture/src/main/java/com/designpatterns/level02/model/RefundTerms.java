package com.designpatterns.level02.model;

public record RefundTerms(boolean partialAllowed, Integer maxRefunds) {

    public static final RefundTerms ANY = new RefundTerms(true, null);
}