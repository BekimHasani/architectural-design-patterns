package com.designpatterns.level09.service.audit;

import com.designpatterns.level09.model.MerchantId;
import com.designpatterns.level09.model.PaymentId;
import com.designpatterns.level09.persistence.AuditEventRepository;
import com.designpatterns.level09.service.payment.PaymentService;
import org.springframework.stereotype.Service;

import java.util.List;

/** A payment's history, read-only. Level 8's AuditTrail did this with SQL; now the SQL sits in persistence. */
@Service
public class AuditService {

    private final AuditEventRepository events;
    private final PaymentService payments;

    public AuditService(AuditEventRepository events, PaymentService payments) {
        this.events = events;
        this.payments = payments;
    }

    /** Throws UnknownPaymentException for another merchant's payment, like every other read. */
    public List<AuditEntry> history(MerchantId merchantId, PaymentId paymentId) {
        payments.current(merchantId, paymentId); // ownership check
        return events.forPayment(paymentId).stream()
                .map(e -> new AuditEntry(e.sequence(), e.type(), e.from().name(), e.to().name(), e.amountMinor(),
                        e.actor(), e.detail(), e.occurredAt()))
                .toList();
    }
}
