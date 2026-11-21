package com.designpatterns.level02.presentation.rest.dto;

import com.designpatterns.level02.model.Payment;
import com.designpatterns.level02.service.payment.PaymentResult;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.net.URI;
import java.util.List;

/** The public response, read from the stored payment. Amounts are derived by the aggregate. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponseDto(String paymentId, String merchantId, String status, String provider, long amount,
                                 long captured, long refunded, long refundable, String currency, String reason,
                                 URI redirectUrl, List<String> allowedActions, List<Attempt> attempts,
                                 List<Operation> operations) {

    public record Attempt(String attemptId, String provider, String outcome) {
    }

    public record Operation(String operationId, String type, Long amount, String status, String reason) {
    }

    /** Mapping from the service result to the DTO happens HERE, at the edge of the system. */
    public static PaymentResponseDto from(PaymentResult result, List<String> allowedActions) {
        Payment p = result.payment();
        return new PaymentResponseDto(p.id().value(), p.merchantId().value(), p.status().name().toLowerCase(),
                p.providerId() == null ? null : p.providerId().value(), p.amount().amountMinor(),
                p.captured().amountMinor(), p.refunded().amountMinor(), p.refundable().amountMinor(),
                p.amount().currency().getCurrencyCode(), p.reason(), p.redirectUrl(), allowedActions,
                result.attempts().stream()
                        .map(a -> new Attempt(a.attemptId(), a.providerId().value(), a.outcome().name())).toList(),
                p.operations().stream()
                        .map(o -> new Operation(o.operationId(), o.type().name(),
                                o.amount() == null ? null : o.amount().amountMinor(), o.status().name(), o.reason()))
                        .toList());
    }
}
