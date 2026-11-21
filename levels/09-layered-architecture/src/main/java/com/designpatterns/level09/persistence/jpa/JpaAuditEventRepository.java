package com.designpatterns.level09.persistence.jpa;

import com.designpatterns.level09.model.PaymentId;
import com.designpatterns.level09.model.PaymentStatus;
import com.designpatterns.level09.persistence.AuditEventRepository;
import com.designpatterns.level09.persistence.StoredPaymentEvent;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class JpaAuditEventRepository implements AuditEventRepository {

    private final SpringDataEvents events;

    JpaAuditEventRepository(SpringDataEvents events) {
        this.events = events;
    }

    @Override
    public List<StoredPaymentEvent> forPayment(PaymentId paymentId) {
        return events.findByPaymentIdOrderBySeq(paymentId.value()).stream()
                .map(e -> new StoredPaymentEvent(e.seq(), e.type(), PaymentStatus.valueOf(e.fromStatus()),
                        PaymentStatus.valueOf(e.toStatus()), e.amountMinor(), e.actor(), e.detail(), e.occurredAt()))
                .toList();
    }
}
