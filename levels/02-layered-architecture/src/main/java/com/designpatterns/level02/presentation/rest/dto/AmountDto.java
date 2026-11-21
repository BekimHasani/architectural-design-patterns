package com.designpatterns.level02.presentation.rest.dto;

import com.designpatterns.level02.model.Money;

/** Body of capture and refund. {@code amountMinor} null = the full amount. */
public record AmountDto(Long amountMinor) {

    /** @return null for "the full amount", which is what the services expect */
    public Money toMoney(String currency) {
        return amountMinor == null ? null : Money.of(amountMinor, currency);
    }
}
