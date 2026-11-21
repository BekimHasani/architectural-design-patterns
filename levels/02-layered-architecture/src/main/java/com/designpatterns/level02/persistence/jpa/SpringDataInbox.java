package com.designpatterns.level02.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data repository for {@link ProviderEventInboxEntity}. Spring generates the implementation from the method names and queries. */
interface SpringDataInbox extends JpaRepository<ProviderEventInboxEntity, ProviderEventInboxEntity.Key> {

    @Transactional
    @Modifying
    @Query("""
            update ProviderEventInboxEntity e set e.outcome = :outcome
            where e.providerId = :providerId and e.eventId = :eventId""")
    int markHandled(@Param("providerId") String providerId, @Param("eventId") String eventId,
                    @Param("outcome") String outcome);
}
