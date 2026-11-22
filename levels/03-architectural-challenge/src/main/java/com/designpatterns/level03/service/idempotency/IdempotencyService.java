package com.designpatterns.level03.service.idempotency;

import com.designpatterns.level03.model.Payment;
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
        Optional<String> previous = keys.findResult(idempotencyKey);
        //todo -> must check the merchant id along with not check only the idemKey,
        if (previous.isPresent()) {
            return previous.get();
        }
        String result = command.get();
        keys.save(idempotencyKey, result);
        return result;
    }
}
