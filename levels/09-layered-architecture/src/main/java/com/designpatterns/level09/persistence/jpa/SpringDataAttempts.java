package com.designpatterns.level09.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Spring Data repository for {@link PaymentAttemptEntity}. Spring generates the implementation from the method names and queries. */
interface SpringDataAttempts extends JpaRepository<PaymentAttemptEntity, String> {

    List<PaymentAttemptEntity> findByPaymentIdOrderByStartedAtAscAttemptIdAsc(String paymentId);
}
