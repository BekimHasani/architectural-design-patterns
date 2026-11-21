package com.designpatterns.level09.persistence;

import com.designpatterns.level09.model.PaymentStatus;

import java.time.Instant;

/**
 * A {@code PaymentEvent} as stored: the model's event plus what only storage knows (sequence and
 * time). Persistence returns its own record instead of a service type, because it may not depend
 * on the layer above it.
 */
public record StoredPaymentEvent(long sequence, String type, PaymentStatus from, PaymentStatus to, Long amountMinor,
                                 String actor, String detail, Instant occurredAt) {
}
