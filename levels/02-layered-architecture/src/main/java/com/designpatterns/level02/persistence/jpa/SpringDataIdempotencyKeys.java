package com.designpatterns.level02.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data repository for {@link IdempotencyKeyEntity}. Spring generates the implementation from the method names and queries. */
interface SpringDataIdempotencyKeys extends JpaRepository<IdempotencyKeyEntity, String> {

    @Transactional
    @Modifying
    @Query("""
            update IdempotencyKeyEntity k set k.state = 'COMPLETED', k.httpStatus = :status, k.responseBody = :body
            where k.key = :key""")
    int complete(@Param("key") String key, @Param("status") int httpStatus, @Param("body") String responseBody);

    @Transactional
    @Modifying
    @Query("delete from IdempotencyKeyEntity k where k.key = :key and k.state = 'IN_PROGRESS'")
    int release(@Param("key") String key);
}
