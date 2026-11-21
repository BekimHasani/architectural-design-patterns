package com.designpatterns.level09.persistence;

/**
 * A stored idempotency key, as the persistence layer hands it out.
 *
 * @param httpStatus   null while in progress
 * @param responseBody null while in progress
 */
public record IdempotencyKeyRecord(String key, String fingerprint, boolean completed, Integer httpStatus,
                                   String responseBody) {
}
