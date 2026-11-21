package com.designpatterns.level03.model;

import com.designpatterns.level03.model.exceptions.PaymentRuleViolationException;

import java.util.Objects;

/**
 * AGGREGATE: our record of money we try to collect from a customer, and of what we gave back.
 * State changes go through the methods below, which enforce the rules of {@link PaymentStatus}.
 */
public final class Payment {

    private final String id;
    private final String merchantId;
    private final String customerId;
    private final Money amount;
    private PaymentStatus status;
    private String providerId;
    private String providerReference;
    private Money refunded;
    private String failureReason;

    private Payment(String id, String merchantId, String customerId, Money amount, PaymentStatus status,
                    String providerId, String providerReference, Money refunded, String failureReason) {
        this.id = Objects.requireNonNull(id, "id");
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.status = Objects.requireNonNull(status, "status");
        this.providerId = providerId;
        this.providerReference = providerReference;
        this.refunded = Objects.requireNonNull(refunded, "refunded");
        this.failureReason = failureReason;
    }

    public static Payment create(String id, String merchantId, String customerId, Money amount) {
        if (amount.isZero()) {
            throw new PaymentRuleViolationException("A payment needs an amount above zero");
        }
        return new Payment(id, merchantId, customerId, amount, PaymentStatus.CREATED, null, null,
                Money.zero(amount.currency()), null);
    }

    /** Rebuilds a stored payment. Used by persistence only. */
    public static Payment restore(String id, String merchantId, String customerId, Money amount, PaymentStatus status,
                                  String providerId, String providerReference, Money refunded, String failureReason) {
        return new Payment(id, merchantId, customerId, amount, status, providerId, providerReference, refunded, failureReason);
    }

    public void markCaptured(String providerId, String providerReference) {
        requireStatus(PaymentStatus.CREATED);
        this.status = PaymentStatus.CAPTURED;
        this.providerId = providerId;
        this.providerReference = providerReference;
    }

    public void markDeclined(String providerId, String reason) {
        requireStatus(PaymentStatus.CREATED);
        this.status = PaymentStatus.DECLINED;
        this.providerId = providerId;
        this.failureReason = reason;
    }

    public void markFailed(String reason) {
        requireStatus(PaymentStatus.CREATED);
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void markOutcomeUnknown(String providerId, String reason) {
        requireStatus(PaymentStatus.CREATED);
        this.status = PaymentStatus.OUTCOME_UNKNOWN;
        this.providerId = providerId;
        this.failureReason = reason;
    }

    /** Checks a refund BEFORE any provider is called. Throws if the refund is not allowed. */
    public void checkRefundable(Money requested) {
        if (!status.isRefundable()) {
            throw new PaymentRuleViolationException("Payment " + id + " is " + status + "; only captured payments can be refunded");
        }
        requested.requireSameCurrency(amount);
        if (requested.isZero()) {
            throw new PaymentRuleViolationException("A refund needs an amount above zero");
        }
        if (requested.isGreaterThan(amount)) {
            throw new PaymentRuleViolationException("Refund of " + requested + " exceeds the captured " + amount);
        }
    }

    /** Records a refund the provider has confirmed. */
    public void recordRefund(Money refundedNow) {
        checkRefundable(refundedNow);
        this.refunded = refunded.plus(refundedNow);
        this.status = refunded.equals(amount) ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
    }

    private void requireStatus(PaymentStatus expected) {
        if (status != expected) {
            throw new PaymentRuleViolationException("Payment " + id + " is " + status + ", expected " + expected);
        }
    }

    public String id() {
        return id;
    }

    public String merchantId() {
        return merchantId;
    }

    public String customerId() {
        return customerId;
    }

    public Money amount() {
        return amount;
    }

    public PaymentStatus status() {
        return status;
    }

    public String providerId() {
        return providerId;
    }

    public String providerReference() {
        return providerReference;
    }

    public Money refunded() {
        return refunded;
    }

    public String failureReason() {
        return failureReason;
    }
}
