package com.designpatterns.level02.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Spring Data repository for the append-only {@link PaymentEventEntity}. Spring generates the implementation from the method names and queries. */
interface SpringDataEvents extends JpaRepository<PaymentEventEntity, Long> {

    List<PaymentEventEntity> findByPaymentIdOrderBySeq(String paymentId);
}
