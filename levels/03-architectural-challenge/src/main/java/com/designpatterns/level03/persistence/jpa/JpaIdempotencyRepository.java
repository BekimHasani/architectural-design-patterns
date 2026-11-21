package com.designpatterns.level03.persistence.jpa;

import com.designpatterns.level03.persistence.IdempotencyRepository;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaIdempotencyRepository implements IdempotencyRepository {

    private final SpringDataIdempotencyKeys rows;

    JpaIdempotencyRepository(SpringDataIdempotencyKeys rows) {
        this.rows = rows;
    }

    @Override
    public Optional<String> findResult(String key) {
        return rows.findById(key).map(row -> row.result);
    }

    @Override
    public void save(String key, String result) {
        rows.save(new IdempotencyKeyEntity(key, result, Instant.now()));
    }
}
