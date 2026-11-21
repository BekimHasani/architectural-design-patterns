package com.designpatterns.level03.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "payments")
class PaymentEntity {

    @Id
    String id;
    @Column(name = "merchant_id")
    String merchantId;
    @Column(name = "customer_id")
    String customerId;
    String currency;
    @Column(name = "amount_minor")
    long amountMinor;
    String status;
    @Column(name = "provider_id")
    String providerId;
    @Column(name = "provider_ref")
    String providerRef;
    @Column(name = "refunded_minor")
    long refundedMinor;
    @Column(name = "failure_reason")
    String failureReason;
    @Column(name = "updated_at")
    Instant updatedAt;

    protected PaymentEntity() {
    }
}
