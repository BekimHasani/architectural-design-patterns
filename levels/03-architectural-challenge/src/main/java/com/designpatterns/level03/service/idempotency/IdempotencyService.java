package com.designpatterns.level03.service.idempotency;

import com.designpatterns.level03.persistence.IdempotencyRepository;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runs a money-moving command at most once per Idempotency-Key. A repeated request gets the result
 * (an id) of the first one back, and the command does not run again.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRepository keys;

    public IdempotencyService(IdempotencyRepository keys) {
        this.keys = keys;
    }

    /**
     * @param merchantId     who sent the request
     * @param operation      what the request does, e.g. {@code "POST /payments"}
     * @param idempotencyKey the client's key for this request
     * @param command        the work to do the first time; returns the id to remember
     */
    public String execute(String merchantId, String operation, String idempotencyKey, Supplier<String> command) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        String scopedKey = scopedKey(merchantId, operation, idempotencyKey);
        Optional<String> previous = keys.findResult(scopedKey);
        if (previous.isPresent()) {
            return previous.get();
        }
        String result = command.get();
        keys.save(scopedKey, result);
        return result;
    }

    /**
     * A client's key only identifies a request together with who sent it and what it does: two
     * merchants may both use "order-42", and a refund reusing a charge's key is a different request.
     * The scope goes into the stored key itself, so the lookup and the table's primary key both
     * work per merchant + operation + key.
     */
    private static String scopedKey(String merchantId, String operation, String idempotencyKey) {
        return merchantId + "|" + operation + "|" + idempotencyKey;
    }
}