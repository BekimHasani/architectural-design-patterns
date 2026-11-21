package com.designpatterns.level02.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * The {@code payments} row. A PERSISTENCE type, not the domain model: it has a no-arg constructor,
 * mutable fields and JPA annotations, which the {@code Payment} aggregate must never get. The adapter
 * maps between the two (DATA MAPPER).
 *
 * <p>Implements {@link Persistable} so that saving a NEW payment is a real INSERT: Spring Data would
 * otherwise MERGE an entity with an assigned id, silently overwriting a row with the same id.
 *
 * <p>{@code version} is a plain column, not JPA's {@code @Version}: the aggregate's version must go up
 * by exactly one on EVERY save, also when only its operations changed and this row didn't. The
 * adapter checks and sets it itself, under a row lock.
 */
@Entity
@Table(name = "payments")
class PaymentEntity implements Persistable<String> {

    @Id
    private String id;
    @Column(name = "merchant_id")
    private String merchantId;
    @Column(name = "customer_id")
    private String customerId;
    private String currency;
    @Column(name = "amount_minor")
    private long amountMinor;
    @Column(name = "capture_mode")
    private String captureMode;
    private String status;
    @Column(name = "provider_id")
    private String providerId;
    @Column(name = "provider_ref")
    private String providerRef;
    @Column(name = "attempt_id")
    private String attemptId;
    @Column(name = "redirect_url")
    private String redirectUrl;
    private String reason;
    private int version;
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

    protected PaymentEntity() {
        // for JPA
    }

    PaymentEntity(String id, String merchantId, String customerId, String currency, long amountMinor,
                  String captureMode) {
        this.id = id;
        this.merchantId = merchantId;
        this.customerId = customerId;
        this.currency = currency;
        this.amountMinor = amountMinor;
        this.captureMode = captureMode;
    }

    /** Copies everything that can change during a payment's life. */
    void update(String status, String providerId, String providerRef, String attemptId, String redirectUrl,
                String reason, int version, Instant updatedAt) {
        this.status = status;
        this.providerId = providerId;
        this.providerRef = providerRef;
        this.attemptId = attemptId;
        this.redirectUrl = redirectUrl;
        this.reason = reason;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    String merchantId() { return merchantId; }
    String customerId() { return customerId; }
    String currency() { return currency; }
    long amountMinor() { return amountMinor; }
    String captureMode() { return captureMode; }
    String status() { return status; }
    String providerId() { return providerId; }
    String providerRef() { return providerRef; }
    String attemptId() { return attemptId; }
    String redirectUrl() { return redirectUrl; }
    String reason() { return reason; }
    int version() { return version; }
}
