package com.designpatterns.level02.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A {@code payment_operations} row. Linked to its payment by id only, with no {@code @ManyToOne}: the
 * aggregate is loaded and saved as a whole by the adapter, so lazy-loading relations would only add
 * surprises (N+1 queries, LazyInitializationException).
 */
@Entity
@Table(name = "payment_operations")
class PaymentOperationEntity {

    @Id
    @Column(name = "operation_id")
    private String operationId;
    @Column(name = "payment_id")
    private String paymentId;
    private String type;
    @Column(name = "amount_minor")
    private Long amountMinor;
    private String status;
    @Column(name = "provider_ref")
    private String providerRef;
    private String reason;
    private int position;

    protected PaymentOperationEntity() {
    }

    PaymentOperationEntity(String operationId, String paymentId, String type, Long amountMinor, int position) {
        this.operationId = operationId;
        this.paymentId = paymentId;
        this.type = type;
        this.amountMinor = amountMinor;
        this.position = position;
    }

    void update(String status, String providerRef, String reason) {
        this.status = status;
        this.providerRef = providerRef;
        this.reason = reason;
    }

    String operationId() { return operationId; }
    String type() { return type; }
    Long amountMinor() { return amountMinor; }
    String status() { return status; }
    String providerRef() { return providerRef; }
    String reason() { return reason; }
}
