package com.designpatterns.simulators;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * A simplified, Adyen-inspired payments API. NOT the real Adyen API.
 *
 * <pre>
 * POST /v71/payments          {amount:{value, currency}, reference, merchantAccount}   X-API-Key: test_...
 *                             Idempotency-Key: same key → same stored answer, no second charge
 * POST /v71/payments/details  {pspReference}   (after the shopper was redirected)
 * GET  /v71/payments?merchantReference=att_1   (simulator-only lookup by OUR reference)
 * </pre>
 * The outcome is in {@code resultCode}: Authorised | Refused | RedirectShopper. A refusal is
 * HTTP 200, so reading the status code alone is a bug. Errors: {@code {status, errorCode, message, errorType}}.
 */
public final class AdyenLikeSimulator extends BaseSimulator {

    public static final int DEFAULT_PORT = 4103;

    private final AtomicLong ids = new AtomicLong(8_800_000_000_000_000L);
    private final Map<String, Map<String, Object>> payments = new LinkedHashMap<>();
    private final Set<String> redirected = new HashSet<>();
    private final Set<String> authenticated = new HashSet<>();
    private final Map<String, Response> idempotentResponses = new HashMap<>();

    private record Response(int status, Object body, Delivery delivery) {
        Response(int status, Object body) {
            this(status, body, Delivery.NORMAL);
        }
    }

    private AdyenLikeSimulator(int port) throws IOException {
        super(port);
        route("/v71/payments", this::handle);
    }

    public static AdyenLikeSimulator start(int port) throws IOException {
        return new AdyenLikeSimulator(port).started();
    }

    @Override
    public synchronized void completeCustomerAction(String pspReference) {
        authenticated.add(pspReference);
    }

    @Override
    protected synchronized void resetState() {
        payments.clear();
        redirected.clear();
        authenticated.clear();
        idempotentResponses.clear();
    }

    private void handle(HttpExchange ex) throws IOException {
        String apiKey = ex.getRequestHeaders().getFirst("X-API-Key");
        if (apiKey == null || !apiKey.startsWith("test_")) {
            SimHttp.writeJson(ex, 401, error(401, "000", "HTTP Status Response - Unauthorized", "security"));
            return;
        }
        sawCredential(apiKey);
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String, Object> body = SimHttp.readJson(ex);
        Response response;
        synchronized (this) {
            if (method.equals("GET") && path.equals("/v71/payments")) {
                response = findByReference(SimHttp.queryParam(ex, "merchantReference"));
            } else if (path.equals("/v71/payments")) {
                response = payIdempotently(body, ex.getRequestHeaders().getFirst("Idempotency-Key"));
            } else if (path.equals("/v71/payments/details")) {
                response = details(String.valueOf(body.get("pspReference")));
            } else if (SimHttp.segments(ex, "/v71/payments").size() == 2) {
                List<String> parts = SimHttp.segments(ex, "/v71/payments");
                response = operation(ex.getRequestHeaders().getFirst("Idempotency-Key"),
                        () -> modification(parts.get(0), parts.get(1), body));
            } else {
                response = new Response(404, error(404, "000", "Not found", "validation"));
            }
        }
        respond(ex, response.status(), response.body(), response.delivery());
    }

    private Response payIdempotently(Map<String, Object> body, String idempotencyKey) {
        if (idempotencyKey != null && idempotentResponses.containsKey(idempotencyKey)) {
            return idempotentResponses.get(idempotencyKey);
        }
        Response response = pay(body);
        if (idempotencyKey != null && worthRemembering(response.status())) {
            idempotentResponses.put(idempotencyKey, new Response(response.status(), response.body()));
        }
        return response;
    }

    /** Capture / cancel / refund: idempotent, and subject to the same delivery faults as a payment. */
    private Response operation(String idempotencyKey, Supplier<Response> work) {
        if (idempotencyKey != null && idempotentResponses.containsKey(idempotencyKey)) {
            return idempotentResponses.get(idempotencyKey);
        }
        Fault fault = faults.next();
        if (fault == Fault.HTTP_503) {
            return new Response(503, error(503, "905", "Service unavailable", "internal"));
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

    @SuppressWarnings("unchecked")
    private Response modification(String psp, String action, Map<String, Object> body) {
        Map<String, Object> payment = payments.get(psp);
        if (payment == null) {
            return new Response(422, error(422, "14_002", "Invalid payment reference", "validation"));
        }
        Map<String, Object> amount = (Map<String, Object>) payment.get("amount");
        long total = SimHttp.asLong(amount.get("value"));
        String currency = (String) amount.get("currency");
        long requested = body.get("amount") instanceof Map<?, ?> m ? SimHttp.asLong(((Map<String, Object>) m).get("value")) : total;
        long captured = (long) payment.getOrDefault("captured", 0L);
        long refunded = (long) payment.getOrDefault("refunded", 0L);
        boolean authorised = "Authorised".equals(payment.get("resultCode"));
        switch (action) {
            case "captures" -> {
                if (!authorised || captured > 0 || requested > total) {
                    return new Response(422, error(422, "167", "Original pspReference required", "validation"));
                }
                payment.put("captured", requested);
                ledger.record(psp, requested, currency);
            }
            case "cancels" -> {
                if (!authorised || captured > 0) {
                    return new Response(422, error(422, "167", "Cannot cancel", "validation"));
                }
                payment.put("resultCode", "Cancelled");
            }
            case "refunds" -> {
                if (captured == 0 || refunded + requested > captured) {
                    return new Response(422, error(422, "167", "Refund amount exceeded", "validation"));
                }
                payment.put("refunded", refunded + requested);
                refundLedger.record(psp, requested, currency);
            }
            default -> {
                return new Response(404, error(404, "000", "Not found", "validation"));
            }
        }
        return new Response(201, Map.of("pspReference", String.valueOf(ids.incrementAndGet()), "paymentPspReference", psp, "status", "received"));
    }

    private Response findByReference(String merchantReference) {
        List<Map<String, Object>> found = payments.values().stream()
                .filter(p -> Objects.equals(p.get("merchantReference"), merchantReference))
                .toList();
        return new Response(200, Map.of("payments", found));
    }

    @SuppressWarnings("unchecked")
    private Response pay(Map<String, Object> body) {
        if (!(body.get("amount") instanceof Map<?, ?> amount) || !amount.containsKey("value")
                || !amount.containsKey("currency") || !body.containsKey("reference")
                || !body.containsKey("merchantAccount")) {
            return new Response(422, error(422, "14_004", "Missing required field", "validation"));
        }
        Map<String, Object> payment = new LinkedHashMap<>();
        String psp = String.valueOf(ids.incrementAndGet());
        payment.put("pspReference", psp);
        payment.put("merchantReference", body.get("reference"));
        payment.put("amount", amount);
        payment.put("manualCapture", Boolean.TRUE.equals(body.get("manualCapture")));

        Fault fault = faults.next();
        switch (fault) {
            case HTTP_500 -> {
                return new Response(500, error(500, "901", "Internal error", "internal"));
            }
            case HTTP_503 -> {
                return new Response(503, error(503, "905", "Service unavailable", "internal"));
            }
            case HTTP_429 -> {
                return new Response(429, error(429, "000", "Too many requests", "throttling"));
            }
            case TIMEOUT, DROP_AFTER_COMMIT -> {
                authorise(payment); // the card IS charged…
                Delivery delivery = fault == Fault.DROP_AFTER_COMMIT ? Delivery.DROP : Delivery.HANG;
                return new Response(200, payment, delivery); // …but no answer arrives
            }
            case DECLINE -> {
                payment.put("resultCode", "Refused");
                payment.put("refusalReason", "Refused");
                payments.put(psp, payment);
                notify(List.of(payment), false);
                return new Response(200, payment); // 200 even though the payment was refused!
            }
            case ASYNC -> {
                payment.put("resultCode", "Received"); // accepted; the outcome comes by notification
                payments.put(psp, payment);
                return new Response(200, payment);
            }
            case REQUIRES_ACTION -> {
                payment.put("resultCode", "RedirectShopper");
                payment.put("action", Map.of("type", "redirect", "method", "GET",
                        "url", baseUrl() + "/sim/redirect/" + psp));
                payments.put(psp, payment);
                redirected.add(psp);
                return new Response(200, payment);
            }
            case NONE -> {
                authorise(payment);
                notify(List.of(payment), true); // delivered BEFORE our HTTP answer
                return new Response(200, payment);
            }
        }
        throw new IllegalStateException();
    }

    /** Authorises EVERY "Received" payment and notifies them all in ONE batch (Adyen style). */
    public synchronized void settleAll() {
        List<Map<String, Object>> settled = payments.values().stream()
                .filter(p -> "Received".equals(p.get("resultCode")))
                .toList();
        settled.forEach(this::authorise);
        if (!settled.isEmpty()) {
            notify(settled, true);
        }
    }

    /**
     * One request, many {@code NotificationRequestItem}s, each with its own
     * {@code additionalData.hmacSignature = base64 hmac(secret, psp:merchantRef:eventCode:success)}.
     * The receiver must answer {@code [accepted]}.
     */
    private void notify(List<Map<String, Object>> batch, boolean success) {
        List<Map<String, Object>> items = batch.stream().map(p -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("eventCode", "AUTHORISATION");
            item.put("success", String.valueOf(success));
            item.put("pspReference", p.get("pspReference"));
            item.put("merchantReference", p.get("merchantReference"));
            item.put("eventDate", java.time.Instant.now().toString());
            item.put("amount", p.get("amount"));
            return item;
        }).toList();
        webhooks.send(secret -> {
            List<Map<String, Object>> signed = items.stream().map(item -> {
                String data = item.get("pspReference") + ":" + item.get("merchantReference") + ":"
                        + item.get("eventCode") + ":" + item.get("success");
                Map<String, Object> copy = new LinkedHashMap<>(item);
                copy.put("additionalData", Map.of("hmacSignature",
                        java.util.Base64.getEncoder().encodeToString(SimHttp.hmacSha256(secret, data))));
                return Map.<String, Object>of("NotificationRequestItem", copy);
            }).toList();
            return new WebhookSender.Signed(SimHttp.json(Map.of("live", "false", "notificationItems", signed)), Map.of());
        }, false);
    }

    private Response details(String psp) {
        Map<String, Object> payment = payments.get(psp);
        if (payment == null) {
            return new Response(422, error(422, "14_002", "Invalid payment reference", "validation"));
        }
        if (redirected.contains(psp) && authenticated.contains(psp)) {
            redirected.remove(psp);
            payment.remove("action");
            authorise(payment);
            notify(List.of(payment), true);
        }
        return new Response(200, payment);
    }

    @SuppressWarnings("unchecked")
    private void authorise(Map<String, Object> payment) {
        payment.put("resultCode", "Authorised");
        payments.put((String) payment.get("pspReference"), payment);
        Map<String, Object> amount = (Map<String, Object>) payment.get("amount");
        if (Boolean.TRUE.equals(payment.get("manualCapture"))) {
            return; // funds are only held until /captures
        }
        payment.put("captured", SimHttp.asLong(amount.get("value")));
        ledger.record((String) payment.get("pspReference"), SimHttp.asLong(amount.get("value")),
                (String) amount.get("currency"));
    }

    private static Map<String, Object> error(int status, String code, String message, String type) {
        return Map.of("status", status, "errorCode", code, "message", message, "errorType", type);
    }
}
