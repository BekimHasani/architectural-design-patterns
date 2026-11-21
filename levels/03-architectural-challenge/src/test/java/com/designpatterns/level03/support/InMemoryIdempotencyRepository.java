package com.designpatterns.level03.support;

import com.designpatterns.level03.persistence.IdempotencyRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Test double: a map instead of a database. */
public final class InMemoryIdempotencyRepository implements IdempotencyRepository {

    public final Map<String, String> rows = new HashMap<>();

    @Override
    public Optional<String> findResult(String key) {
        return Optional.ofNullable(rows.get(key));
    }

    @Override
    public void save(String key, String result) {
        rows.put(key, result);
    }
}
