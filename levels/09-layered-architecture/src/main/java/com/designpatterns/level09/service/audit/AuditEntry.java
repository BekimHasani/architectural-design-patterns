package com.designpatterns.level09.service.audit;

import java.time.Instant;

/** One line of a payment's history: what happened, who did it, when, and why. */
public record AuditEntry(long sequence, String type, String fromStatus, String toStatus, Long amountMinor,
                         String actor, String detail, Instant occurredAt) {
}
