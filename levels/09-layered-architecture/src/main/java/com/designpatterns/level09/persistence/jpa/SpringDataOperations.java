package com.designpatterns.level09.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Spring Data repository for {@link PaymentOperationEntity}. Spring generates the implementation from the method names and queries. */
interface SpringDataOperations extends JpaRepository<PaymentOperationEntity, String> {

    List<PaymentOperationEntity> findByPaymentIdOrderByPosition(String paymentId);
}
