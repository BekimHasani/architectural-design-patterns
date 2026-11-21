package com.designpatterns.level03.persistence;

import java.util.Optional;

/** Remembers the result (an id) of the first request made with an idempotency key. */
public interface IdempotencyRepository {

    Optional<String> findResult(String key);

    void save(String key, String result);
}
