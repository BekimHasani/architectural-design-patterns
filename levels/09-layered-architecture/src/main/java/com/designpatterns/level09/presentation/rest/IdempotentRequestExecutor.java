package com.designpatterns.level09.presentation.rest;

import com.designpatterns.level09.model.MerchantId;
import com.designpatterns.level09.service.idempotency.IdempotencyClaim;
import com.designpatterns.level09.service.idempotency.IdempotencyService;
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
 * EXECUTE AROUND: wraps the real work of every money-moving command with the idempotency protocol
 * (claim → work → store the answer, or release on failure). Every such command needs an
 * {@code Idempotency-Key}. Keys are scoped per merchant and per path: two merchants can't collide, and
 * one key can't be reused across different commands.
 *
 * <p>The HTTP side (header, status codes, JSON) is presentation; the rules ("is this a replay?") are in
 * {@link IdempotencyService}.
 */
@Component
class IdempotentRequestExecutor {

    private final IdempotencyService idempotency;
    private final ObjectMapper json;

    IdempotentRequestExecutor(IdempotencyService idempotency, ObjectMapper json) {
        this.idempotency = idempotency;
        this.json = json;
    }

    ResponseEntity<String> execute(MerchantId merchant, String path, String key, String requestBody,
                                   Supplier<ResponseEntity<?>> work) {
        if (key == null || key.isBlank()) {
            return problem(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }
        // scope: merchant + path + the client's key. Two merchants may pick the same key without clashing.
        String scopedKey = merchant + ":" + path + ":" + key;
        // FLOW A.3a: the service decides what the key's state means; we only map it to HTTP.
        // The fingerprint (hash of the body) catches a key reused for a DIFFERENT request.
        return switch (idempotency.claim(scopedKey, fingerprint(requestBody))) {
            case IdempotencyClaim.Completed done -> ResponseEntity.status(done.httpStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotent-Replayed", "true")
                    .body(done.responseBody());
            case IdempotencyClaim.InProgress busy ->
                    problem(HttpStatus.CONFLICT, "A request with this Idempotency-Key is in progress");
            case IdempotencyClaim.Mismatch reused -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This Idempotency-Key was used for a different request");
            case IdempotencyClaim.Claimed first -> runAndStore(scopedKey, work); // we are first: do the work
        };
    }

    private ResponseEntity<String> runAndStore(String key, Supplier<ResponseEntity<?>> work) {
        try {
            ResponseEntity<?> response = work.get();                       // the real command (FLOW A.4 ...)
            String body = json.writeValueAsString(response.getBody());
            idempotency.complete(key, response.getStatusCode().value(), body); // FLOW A.16: remember the answer
            return ResponseEntity.status(response.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (RuntimeException e) {
            idempotency.release(key); // e.g. a rule said no: nothing happened, the client may try again
            throw e;
        } catch (JsonProcessingException e) {
            idempotency.release(key);
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
