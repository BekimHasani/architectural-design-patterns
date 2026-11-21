package com.designpatterns.simulators;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simplified, Stripe-inspired payment API. NOT the real Stripe API.
 *
 * <pre>
 * POST /v1/payment_intents              {amount, currency, confirm, metadata}   Authorization: Bearer sk_test_...
 *                                        Idempotency-Key: same key → same stored answer, no second charge
 * GET  /v1/payment_intents/{id}
 * GET  /v1/payment_intents/search?query=metadata['attempt_id']:'att_1'
 * POST /v1/payment_intents/{id}/confirm  (after 3-D Secure)
 * </pre>
 * Amounts are integer minor units; currency is lower-case. Errors: {@code {"error": {type, code, decline_code}}}.
 */
public final class StripeLikeSimulator extends BaseSimulator {

    public static final int DEFAULT_PORT = 4101;
    private static final String PATH = "/v1/payment_intents";
    private static final Pattern METADATA_QUERY = Pattern.compile("metadata\\['(\\w+)']:'([^']*)'");

    private final AtomicLong ids = new AtomicLong();
    private final Map<String, Map<String, Object>> intents = new LinkedHashMap<>();
    private final Set<String> authenticated = new HashSet<>();
    private final Map<String, Response> idempotentResponses = new HashMap<>();

    private record Response(int status, Object body, Delivery delivery) {
        Response(int status, Object body) {
            this(status, body, Delivery.NORMAL);
        }
    }

    private StripeLikeSimulator(int port) throws IOException {
        super(port);
        route(PATH, this::handle);
        route("/v1/refunds", this::refundRoute);
    }

    /** Starts the simulator; pass port 0 to get a free random port (used by tests). */
    public static StripeLikeSimulator start(int port) throws IOException {
        return new StripeLikeSimulator(port).started();
    }

    @Override
    public synchronized void completeCustomerAction(String paymentIntentId) {
        authenticated.add(paymentIntentId);
    }

    @Override
    protected synchronized void resetState() {
        intents.clear();
        authenticated.clear();
        idempotentResponses.clear();
    }

    private void handle(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer sk_test_")) {
            SimHttp.writeJson(ex, 401, error("invalid_request_error", "api_key_invalid", null));
            return;
        }
        sawCredential(auth.substring("Bearer ".length()));
        List<String> parts = SimHttp.segments(ex, PATH);
        String method = ex.getRequestMethod();
        Response response;
        synchronized (this) {
            if (method.equals("POST") && parts.isEmpty()) {
                response = create(SimHttp.readJson(ex), ex.getRequestHeaders().getFirst("Idempotency-Key"));
            } else if (method.equals("GET") && parts.equals(List.of("search"))) {
                response = search(ex.getRequestURI().getRawQuery());
            } else if (method.equals("GET") && parts.size() == 1) {
                response = find(parts.get(0));
            } else if (method.equals("POST") && parts.size() == 2 && parts.get(1).equals("confirm")) {
                response = confirm(parts.get(0));
            } else if (method.equals("POST") && parts.size() == 2 && parts.get(1).equals("capture")) {
                Map<String, Object> body = SimHttp.readJson(ex);
                response = operation(ex.getRequestHeaders().getFirst("Idempotency-Key"), () -> capture(parts.get(0), body));
            } else if (method.equals("POST") && parts.size() == 2 && parts.get(1).equals("cancel")) {
                response = operation(ex.getRequestHeaders().getFirst("Idempotency-Key"), () -> cancel(parts.get(0)));
            } else {
                response = new Response(404, error("invalid_request_error", "resource_missing", null));
            }
        }
        respond(ex, response.status(), response.body(), response.delivery());
    }

    private Response create(Map<String, Object> body, String idempotencyKey) {
        if (idempotencyKey != null && idempotentResponses.containsKey(idempotencyKey)) {
            return idempotentResponses.get(idempotencyKey);
        }
        Response response = createIntent(body);
        if (idempotencyKey != null && worthRemembering(response.status())) {
            // a retry with the same key gets the real answer, delivered normally
            idempotentResponses.put(idempotencyKey, new Response(response.status(), response.body()));
        }
        return response;
    }

    /** Capture / cancel / refund: idempotent, and subject to the same delivery faults as a create. */
    private Response operation(String idempotencyKey, Supplier<Response> work) {
        if (idempotencyKey != null && idempotentResponses.containsKey(idempotencyKey)) {
            return idempotentResponses.get(idempotencyKey);
        }
        Fault fault = faults.next();
        if (fault == Fault.HTTP_503) {
            return new Response(503, error("api_error", "service_unavailable", null));
        }
        Response response = work.get();
        if (idempotencyKey != null && worthRemembering(response.status())) {
            idempotentResponses.put(idempotencyKey, new Response(response.status(), response.body()));
        }
        return switch (fault) {
            case DROP_AFTER_COMMIT -> new Response(response.status(), response.body(), Delivery.DROP);
            case TIMEOUT -> new Response(response.status(), response.body(), Delivery.HANG);
            default -> response;
        };
    }

    private Response capture(String id, Map<String, Object> body) {
        Map<String, Object> intent = intents.get(id);
        if (intent == null) {
            return new Response(404, error("invalid_request_error", "resource_missing", null));
        }
        if (!"requires_capture".equals(intent.get("status"))) {
            return new Response(400, error("invalid_request_error", "payment_intent_unexpected_state", null));
        }
        long amount = body.containsKey("amount_to_capture") ? SimHttp.asLong(body.get("amount_to_capture")) : (long) intent.get("amount");
        if (amount > (long) intent.get("amount")) {
            return new Response(400, error("invalid_request_error", "amount_too_large", null));
        }
        intent.put("status", "succeeded");
        intent.put("amount_received", amount);
        ledger.record(id, amount, ((String) intent.get("currency")).toUpperCase());
        return new Response(200, intent);
    }

    private Response cancel(String id) {
        Map<String, Object> intent = intents.get(id);
        if (intent == null) {
            return new Response(404, error("invalid_request_error", "resource_missing", null));
        }
        if (!"requires_capture".equals(intent.get("status"))) {
            return new Response(400, error("invalid_request_error", "payment_intent_unexpected_state", null));
        }
        intent.put("status", "canceled");
        return new Response(200, intent);
    }

    private void refundRoute(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer sk_test_")) {
            SimHttp.writeJson(ex, 401, error("invalid_request_error", "api_key_invalid", null));
            return;
        }
        sawCredential(auth.substring("Bearer ".length()));
        Map<String, Object> body = SimHttp.readJson(ex);
        Response response;
        synchronized (this) {
            response = operation(ex.getRequestHeaders().getFirst("Idempotency-Key"), () -> refund(body));
        }
        respond(ex, response.status(), response.body(), response.delivery());
    }

    private Response refund(Map<String, Object> body) {
        String id = String.valueOf(body.get("payment_intent"));
        Map<String, Object> intent = intents.get(id);
        if (intent == null || !"succeeded".equals(intent.get("status"))) {
            return new Response(400, error("invalid_request_error", "charge_not_refundable", null));
        }
        long received = (long) intent.getOrDefault("amount_received", intent.get("amount"));
        long already = (long) intent.getOrDefault("amount_refunded", 0L);
        long amount = body.containsKey("amount") ? SimHttp.asLong(body.get("amount")) : received - already;
        if (already + amount > received) {
            return new Response(400, error("invalid_request_error", "charge_already_refunded", null));
        }
        intent.put("amount_refunded", already + amount);
        refundLedger.record(id, amount, ((String) intent.get("currency")).toUpperCase());
        return new Response(200, Map.of("id", "re_" + ids.incrementAndGet(), "status", "succeeded", "amount", amount));
    }

    private Response createIntent(Map<String, Object> body) {
        if (!body.containsKey("amount") || !body.containsKey("currency")) {
            return new Response(400, error("invalid_request_error", "parameter_missing", null));
        }
        Fault fault = faults.next();
        switch (fault) {
            case DECLINE -> {
                return new Response(402, error("card_error", "card_declined", "generic_decline"));
            }
            case HTTP_500 -> {
                return new Response(500, error("api_error", "internal_error", null));
            }
            case HTTP_503 -> {
                return new Response(503, error("api_error", "service_unavailable", null));
            }
            case HTTP_429 -> {
                return new Response(429, error("rate_limit_error", "rate_limit", null));
            }
            case TIMEOUT, DROP_AFTER_COMMIT -> {
                Map<String, Object> intent = newIntent(body, "succeeded");
                finishAuthorization(intent); // the card IS charged (or authorized)…
                Delivery delivery = fault == Fault.DROP_AFTER_COMMIT ? Delivery.DROP : Delivery.HANG;
                return new Response(200, intent, delivery); // …but no answer arrives
            }
            case REQUIRES_ACTION -> {
                Map<String, Object> intent = newIntent(body, "requires_action");
                intent.put("next_action", Map.of("type", "redirect_to_url",
                        "redirect_to_url", Map.of("url", baseUrl() + "/sim/3ds/" + intent.get("id"))));
                return new Response(200, intent);
            }
            case ASYNC -> {
                Map<String, Object> intent = newIntent(body, "processing");
                webhook("payment_intent.processing", intent, true);
                return new Response(200, intent);
            }
            case NONE -> {
                Map<String, Object> intent = newIntent(body, "succeeded");
                finishAuthorization(intent);
                notifyFinished(intent); // delivered BEFORE our HTTP answer
                return new Response(200, intent);
            }
        }
        throw new IllegalStateException();
    }

    /** Finishes a "processing" payment (ASYNC fault) and sends the final webhook. */
    public synchronized void settle(String paymentIntentId, boolean success) {
        Map<String, Object> intent = intents.get(paymentIntentId);
        if (intent == null || !"processing".equals(intent.get("status"))) {
            throw new IllegalStateException("Not processing: " + paymentIntentId);
        }
        if (success) {
            finishAuthorization(intent);
            notifyFinished(intent);
        } else {
            intent.put("status", "requires_payment_method");
            webhook("payment_intent.payment_failed", intent, false);
        }
    }

    /** Stripe-style event, signed as {@code Sim-Signature: t=<unix>,v1=<hex hmac(secret, t + "." + body)>}. */
    private void webhook(String type, Map<String, Object> intent, boolean intermediate) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "evt_" + ids.incrementAndGet());
        event.put("object", "event");
        event.put("type", type);
        event.put("created", java.time.Instant.now().getEpochSecond());
        event.put("data", Map.of("object", new LinkedHashMap<>(intent)));
        String body = SimHttp.json(event);
        webhooks.send(secret -> {
            long t = java.time.Instant.now().getEpochSecond();
            String v1 = java.util.HexFormat.of().formatHex(SimHttp.hmacSha256(secret, t + "." + body));
            return new WebhookSender.Signed(body, Map.of("Sim-Signature", "t=" + t + ",v1=" + v1));
        }, intermediate);
    }

    /** "Did you process attempt X?" Lets the client find a payment whose response it never received. */
    private Response search(String rawQuery) {
        String query = rawQuery == null ? "" : URLDecoder.decode(rawQuery.replaceFirst("^query=", ""), StandardCharsets.UTF_8);
        Matcher m = METADATA_QUERY.matcher(query);
        if (!m.matches()) {
            return new Response(400, error("invalid_request_error", "invalid_search_query", null));
        }
        List<Map<String, Object>> found = intents.values().stream()
                .filter(intent -> intent.get("metadata") instanceof Map<?, ?> md && Objects.equals(md.get(m.group(1)), m.group(2)))
                .toList();
        return new Response(200, Map.of("object", "search_result", "data", found));
    }

    private Response find(String id) {
        Map<String, Object> intent = intents.get(id);
        return intent == null
                ? new Response(404, error("invalid_request_error", "resource_missing", null))
                : new Response(200, intent);
    }

    private Response confirm(String id) {
        Map<String, Object> intent = intents.get(id);
        if (intent == null) {
            return new Response(404, error("invalid_request_error", "resource_missing", null));
        }
        if ("requires_action".equals(intent.get("status")) && authenticated.contains(id)) {
            intent.remove("next_action");
            finishAuthorization(intent);
            notifyFinished(intent);
        }
        return new Response(200, intent);
    }

    private Map<String, Object> newIntent(Map<String, Object> body, String status) {
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("id", "pi_" + ids.incrementAndGet());
        intent.put("object", "payment_intent");
        intent.put("amount", SimHttp.asLong(body.get("amount")));
        intent.put("currency", String.valueOf(body.get("currency")));
        intent.put("status", status);
        intent.put("metadata", body.getOrDefault("metadata", Map.of()));
        intent.put("capture_method", body.getOrDefault("capture_method", "automatic"));
        intents.put((String) intent.get("id"), intent);
        return intent;
    }

    /** capture_method=manual: funds are only held, nothing is charged until capture. */
    private void finishAuthorization(Map<String, Object> intent) {
        if ("manual".equals(intent.get("capture_method"))) {
            intent.put("status", "requires_capture");
        } else {
            intent.put("status", "succeeded");
            charge(intent);
        }
    }

    private void notifyFinished(Map<String, Object> intent) {
        webhook("requires_capture".equals(intent.get("status"))
                ? "payment_intent.amount_capturable_updated" : "payment_intent.succeeded", intent, false);
    }

    private void charge(Map<String, Object> intent) {
        ledger.record((String) intent.get("id"), (long) intent.get("amount"),
                ((String) intent.get("currency")).toUpperCase());
    }

    private static Map<String, Object> error(String type, String code, String declineCode) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("type", type);
        err.put("code", code);
        if (declineCode != null) {
            err.put("decline_code", declineCode);
        }
        return Map.of("error", err);
    }
}
