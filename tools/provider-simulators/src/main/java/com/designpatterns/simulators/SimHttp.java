package com.designpatterns.simulators;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Tiny JSON-over-HTTP helpers shared by the simulators. Test tooling, not architecture. */
final class SimHttp {

    static final ObjectMapper JSON = new ObjectMapper();

    private SimHttp() {
    }

    static Map<String, Object> readJson(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return Map.of();
            }
            return JSON.readValue(bytes, new TypeReference<>() {
            });
        }
    }

    static void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (status == 429) {
            exchange.getResponseHeaders().set("Retry-After", "1");
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Non-empty path segments after {@code prefix}: "/v1/x/pi_1/confirm" with prefix "/v1/x" → [pi_1, confirm]. */
    static List<String> segments(HttpExchange exchange, String prefix) {
        return Arrays.stream(exchange.getRequestURI().getPath().substring(prefix.length()).split("/"))
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** The decoded value of one query parameter, or null. */
    static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(kv -> kv.length == 2 && kv[0].equals(name))
                .map(kv -> URLDecoder.decode(kv[1], StandardCharsets.UTF_8))
                .findFirst()
                .orElse(null);
    }

    static byte[] hmacSha256(String secret, String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }
}
