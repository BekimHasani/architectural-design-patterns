package com.designpatterns.level01.api;

import com.designpatterns.level01.payments.application.ports.IdempotencyStore;
import com.designpatterns.level01.payments.application.ports.IdempotencyStore.Claim;
import com.designpatterns.level01.payments.domain.MerchantId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Every command that can move money needs an {@code Idempotency-Key}. Keys are scoped per merchant
 * and per path: two merchants can't collide, and one key can't be reused across different commands.
 */
@Component
class IdempotentRequests {

    private final IdempotencyStore store;
    private final ObjectMapper json;

    IdempotentRequests(IdempotencyStore store, ObjectMapper json) {
        this.store = store;
        this.json = json;
    }

    ResponseEntity<String> execute(MerchantId merchant, String path, String key, String requestBody,
                                   Supplier<ResponseEntity<?>> work) {
        if (key == null || key.isBlank()) {
            return problem(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }
        String scopedKey = merchant + ":" + path + ":" + key;
        return switch (store.claim(scopedKey, fingerprint(requestBody))) {
            case Claim.Completed done -> ResponseEntity.status(done.httpStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotent-Replayed", "true")
                    .body(done.responseBody());
            case Claim.InProgress busy -> problem(HttpStatus.CONFLICT, "A request with this Idempotency-Key is in progress");
            case Claim.Mismatch reused -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This Idempotency-Key was used for a different request");
            case Claim.Claimed first -> runAndStore(scopedKey, work);
        };
    }

    private ResponseEntity<String> runAndStore(String key, Supplier<ResponseEntity<?>> work) {
        try {
            ResponseEntity<?> response = work.get();
            String body = json.writeValueAsString(response.getBody());
            store.complete(key, response.getStatusCode().value(), body);
            return ResponseEntity.status(response.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (RuntimeException e) {
            store.release(key); // e.g. a rule said no: nothing happened, the client may try again
            throw e;
        } catch (JsonProcessingException e) {
            store.release(key);
            throw new IllegalStateException(e);
        }
    }

    private ResponseEntity<String> problem(HttpStatus status, String detail) {
        try {
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(json.writeValueAsString(ProblemDetail.forStatusAndDetail(status, detail)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String fingerprint(String body) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
