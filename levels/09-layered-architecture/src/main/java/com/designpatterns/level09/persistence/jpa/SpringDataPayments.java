package com.designpatterns.level09.persistence.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link PaymentEntity}. Package-private: only {@link JpaPaymentRepository} uses it. Spring generates the implementation from the method names and queries. */
interface SpringDataPayments extends JpaRepository<PaymentEntity, String> {

    /** SELECT … FOR UPDATE: held only inside the adapter's short save transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.id = :id")
    Optional<PaymentEntity> findForUpdate(@Param("id") String id);

    List<PaymentEntity> findByStatusOrderByUpdatedAt(String status);

    @Query("""
            select p from PaymentEntity p where p.id in
                (select o.paymentId from PaymentOperationEntity o where o.status = 'UNKNOWN')
            order by p.updatedAt""")
    List<PaymentEntity> findWithUnknownOperations();
}
