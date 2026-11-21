package com.designpatterns.simulators;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * A simplified, PayPal-inspired orders API. NOT the real PayPal API.
 *
 * <pre>
 * POST /v1/oauth2/token                      Basic base64(clientId:secret)  →  {access_token, expires_in}
 * POST /v2/checkout/orders                   {intent, purchase_units:[{amount:{currency_code, value:"19.99"}}]}
 *                                            PayPal-Request-Id: same id → same stored answer
 * GET  /v2/checkout/orders/{id}
 * GET  /v2/checkout/orders?reference_id=att_1   (simulator-only lookup by OUR reference)
 * POST /v2/checkout/orders/{id}/capture      only after the buyer approved
 * POST /sim/orders/{id}/approve              stands in for the buyer clicking "Approve" at PayPal
 * </pre>
 * Amounts are DECIMAL STRINGS. Errors: {@code {"name": "...", "details": [{"issue": "..."}]}}.
 */
public final class PayPalLikeSimulator extends BaseSimulator {

    public static final int DEFAULT_PORT = 4102;
    public static final String CLIENT_ID = "sim-client";
    public static final String CLIENT_SECRET = "sim-secret";
    private static final String ORDERS = "/v2/checkout/orders";
    /** This simulated PayPal can't take e.g. JPY, which makes routing conflicts real. */
    public static final Set<String> SUPPORTED_CURRENCIES = Set.of("EUR", "USD", "GBP");

    private final AtomicLong ids = new AtomicLong();
    private final AtomicInteger tokenRequests = new AtomicInteger();
    private final Set<String> validTokens = new HashSet<>();
    private final Map<String, Map<String, Object>> orders = new HashMap<>();
    private final Set<String> declineOnCapture = new HashSet<>();

    private final Map<String, Response> idempotentResponses = new HashMap<>();

    private record Response(int status, Object body, Delivery delivery) {
        Response(int status, Object body) {
            this(status, body, Delivery.NORMAL);
        }
    }

    private PayPalLikeSimulator(int port) throws IOException {
        super(port);
        route("/v1/oauth2/token", this::token);
        route(ORDERS, this::handleOrders);
        route("/v2/payments/captures", this::refundRoute);
        route("/sim/orders/", ex -> {
            completeCustomerAction(SimHttp.segments(ex, "/sim/orders").get(0));
            SimHttp.writeJson(ex, 200, Map.of("ok", true));
        });
    }

    public static PayPalLikeSimulator start(int port) throws IOException {
        return new PayPalLikeSimulator(port).started();
    }

    /** How many OAuth tokens were issued; lets tests check that the adapter caches its token. */
    public int tokenRequests() {
        return tokenRequests.get();
    }

    @Override
    public synchronized void completeCustomerAction(String orderId) {
        Map<String, Object> order = orders.get(orderId);
        if (order != null && "CREATED".equals(order.get("status"))) {
            order.put("status", "APPROVED");
            webhook("CHECKOUT.ORDER.APPROVED", Map.of("id", orderId, "status", "APPROVED"));
        }
    }

    @Override
    protected synchronized void resetState() {
        orders.clear();
        declineOnCapture.clear();
        validTokens.clear();
        idempotentResponses.clear();
    }

    private void token(HttpExchange ex) throws IOException {
        String credentials = ex.getRequestHeaders().getFirst("Authorization");
        if (credentials == null || !validClient(credentials)) {
            SimHttp.writeJson(ex, 401, Map.of("error", "invalid_client", "error_description", "Client Authentication failed"));
            return;
        }
        sawCredential(ex.getRequestHeaders().getFirst("Authorization"));
        String token = "A21AA" + UUID.randomUUID();
        synchronized (this) {
            validTokens.add(token);
        }
        tokenRequests.incrementAndGet();
        SimHttp.writeJson(ex, 200, Map.of("access_token", token, "token_type", "Bearer", "expires_in", 32400));
    }

    /** The default client, or any other client whose secret starts with "sim-secret" (one per merchant). */
    private static boolean validClient(String basicAuth) {
        try {
            String decoded = new String(Base64.getDecoder().decode(basicAuth.replaceFirst("^Basic ", "")), StandardCharsets.UTF_8);
            return decoded.equals(CLIENT_ID + ":" + CLIENT_SECRET) || decoded.split(":", 2)[1].startsWith(CLIENT_SECRET);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void handleOrders(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        Response response;
        synchronized (this) {
            if (auth == null || !validTokens.contains(auth.replaceFirst("^Bearer ", ""))) {
                response = new Response(401, Map.of("name", "AUTHENTICATION_FAILURE",
                        "message", "Authentication failed due to invalid authentication credentials or a missing Authorization header."));
            } else {
                List<String> parts = SimHttp.segments(ex, ORDERS);
                String method = ex.getRequestMethod();
                if (method.equals("POST") && parts.isEmpty()) {
                    response = createIdempotently(SimHttp.readJson(ex), ex.getRequestHeaders().getFirst("PayPal-Request-Id"));
                } else if (method.equals("GET") && parts.isEmpty()) {
                    response = findByReference(SimHttp.queryParam(ex, "reference_id"));
                } else if (method.equals("GET") && parts.size() == 1) {
                    response = find(parts.get(0));
                } else if (method.equals("POST") && parts.size() == 2 && parts.get(1).equals("capture")) {
                    Map<String, Object> body = SimHttp.readJson(ex);
                    response = operation(ex.getRequestHeaders().getFirst("PayPal-Request-Id"), () -> capture(parts.get(0), body));
                } else if (method.equals("POST") && parts.size() == 2 && parts.get(1).equals("void")) {
                    response = operation(ex.getRequestHeaders().getFirst("PayPal-Request-Id"), () -> voidOrder(parts.get(0)));
                } else {
                    response = new Response(404, Map.of("name", "RESOURCE_NOT_FOUND"));
                }
            }
        }
        respond(ex, response.status(), response.body(), response.delivery());
    }

    private Response createIdempotently(Map<String, Object> body, String requestId) {
        if (requestId != null && idempotentResponses.containsKey(requestId)) {
            return idempotentResponses.get(requestId);
        }
        Response response = create(body);
        if (requestId != null && worthRemembering(response.status())) {
            idempotentResponses.put(requestId, new Response(response.status(), response.body()));
        }
        return response;
    }

    /** Capture / void / refund: idempotent, and subject to the same delivery faults as a create. */
    private Response operation(String requestId, Supplier<Response> work) {
        if (requestId != null && idempotentResponses.containsKey(requestId)) {
            return idempotentResponses.get(requestId);
        }
        Fault fault = faults.next();
        if (fault == Fault.HTTP_503) {
            return new Response(503, Map.of("name", "SERVICE_UNAVAILABLE"));
        }
        Response response = work.get();
        if (requestId != null && worthRemembering(response.status())) {
            idempotentResponses.put(requestId, new Response(response.status(), response.body()));
        }
        return switch (fault) {
            case DROP_AFTER_COMMIT -> new Response(response.status(), response.body(), Delivery.DROP);
            case TIMEOUT -> new Response(response.status(), response.body(), Delivery.HANG);
            default -> response;
        };
    }

    private Response voidOrder(String id) {
        Map<String, Object> order = orders.get(id);
        if (order == null) {
            return new Response(404, Map.of("name", "RESOURCE_NOT_FOUND"));
        }
        if (!List.of("CREATED", "APPROVED").contains(order.get("status"))) {
            return new Response(422, Map.of("name", "UNPROCESSABLE_ENTITY", "details", List.of(Map.of("issue", "ORDER_CANNOT_BE_VOIDED"))));
        }
        order.put("status", "VOIDED");
        return new Response(200, view(order));
    }

    private void refundRoute(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        Map<String, Object> body = SimHttp.readJson(ex);
        Response response;
        synchronized (this) {
            if (auth == null || !validTokens.contains(auth.replaceFirst("^Bearer ", ""))) {
                response = new Response(401, Map.of("name", "AUTHENTICATION_FAILURE"));
            } else {
                String captureId = SimHttp.segments(ex, "/v2/payments/captures").get(0);
                response = operation(ex.getRequestHeaders().getFirst("PayPal-Request-Id"), () -> refund(captureId, body));
            }
        }
        respond(ex, response.status(), response.body(), response.delivery());
    }

    @SuppressWarnings("unchecked")
    private Response refund(String captureId, Map<String, Object> body) {
        Map<String, Object> order = orders.get(captureId.replaceFirst("^CAP", ""));
        if (order == null || !"COMPLETED".equals(order.get("status"))) {
            return new Response(404, Map.of("name", "RESOURCE_NOT_FOUND"));
        }
        Map<String, Object> captured = (Map<String, Object>) order.get("captured");
        long capturedMinor = toMinor(captured);
        long already = (long) order.getOrDefault("refunded", 0L);
        long amount = body.get("amount") instanceof Map<?, ?> a ? toMinor((Map<String, Object>) a) : capturedMinor - already;
        if (already + amount > capturedMinor) {
            return new Response(422, Map.of("name", "UNPROCESSABLE_ENTITY", "details", List.of(Map.of("issue", "REFUND_AMOUNT_EXCEEDED"))));
        }
        order.put("refunded", already + amount);
        refundLedger.record((String) order.get("id"), amount, (String) captured.get("currency_code"));
        return new Response(201, Map.of("id", "RF" + ids.incrementAndGet(), "status", "COMPLETED"));
    }

    private Response findByReference(String referenceId) {
        List<Map<String, Object>> found = orders.values().stream()
                .filter(order -> Objects.equals(order.get("reference_id"), referenceId))
                .map(this::view)
                .toList();
        return new Response(200, Map.of("orders", found));
    }

    @SuppressWarnings("unchecked")
    private Response create(Map<String, Object> body) {
        Map<String, Object> amount;
        Object referenceId;
        try {
            List<Map<String, Object>> units = (List<Map<String, Object>>) body.get("purchase_units");
            amount = (Map<String, Object>) units.get(0).get("amount");
            referenceId = units.get(0).get("reference_id");
            toMinor(amount); // validates value format against the currency's decimals
        } catch (RuntimeException e) {
            return new Response(400, Map.of("name", "INVALID_REQUEST",
                    "details", List.of(Map.of("issue", "INVALID_PARAMETER_VALUE"))));
        }
        if (!SUPPORTED_CURRENCIES.contains((String) amount.get("currency_code"))) {
            return new Response(422, Map.of("name", "UNPROCESSABLE_ENTITY",
                    "details", List.of(Map.of("issue", "CURRENCY_NOT_SUPPORTED"))));
        }
        Fault fault = faults.next();
        switch (fault) {
            case HTTP_500 -> {
                return new Response(500, Map.of("name", "INTERNAL_SERVER_ERROR"));
            }
            case HTTP_503 -> {
                return new Response(503, Map.of("name", "SERVICE_UNAVAILABLE"));
            }
            case HTTP_429 -> {
                return new Response(429, Map.of("name", "RATE_LIMIT_REACHED"));
            }
            case DECLINE, REQUIRES_ACTION, NONE, TIMEOUT, DROP_AFTER_COMMIT, ASYNC -> {
                // PayPal ALWAYS needs buyer approval; a decline only shows up at capture time.
                String id = "PP" + ids.incrementAndGet();
                if (fault == Fault.DECLINE) {
                    declineOnCapture.add(id);
                }
                Map<String, Object> order = new LinkedHashMap<>();
                order.put("id", id);
                order.put("status", "CREATED");
                order.put("amount", amount);
                order.put("reference_id", referenceId);
                orders.put(id, order);
                Delivery delivery = switch (fault) {
                    case TIMEOUT -> Delivery.HANG;
                    case DROP_AFTER_COMMIT -> Delivery.DROP;
                    default -> Delivery.NORMAL;
                };
                return new Response(201, view(order), delivery);
            }
        }
        throw new IllegalStateException();
    }

    private Response find(String id) {
        Map<String, Object> order = orders.get(id);
        return order == null ? new Response(404, Map.of("name", "RESOURCE_NOT_FOUND")) : new Response(200, view(order));
    }

    @SuppressWarnings("unchecked")
    private Response capture(String id, Map<String, Object> body) {
        Map<String, Object> order = orders.get(id);
        if (order == null) {
            return new Response(404, Map.of("name", "RESOURCE_NOT_FOUND"));
        }
        String status = (String) order.get("status");
        if (!status.equals("APPROVED")) {
            String issue = status.equals("COMPLETED") ? "ORDER_ALREADY_CAPTURED" : "ORDER_NOT_APPROVED";
            return new Response(422, Map.of("name", "UNPROCESSABLE_ENTITY", "details", List.of(Map.of("issue", issue))));
        }
        if (declineOnCapture.remove(id)) {
            return new Response(422, Map.of("name", "UNPROCESSABLE_ENTITY",
                    "details", List.of(Map.of("issue", "INSTRUMENT_DECLINED"))));
        }
        order.put("status", "COMPLETED");
        Map<String, Object> amount = body.get("amount") instanceof Map<?, ?> partial
                ? (Map<String, Object>) partial : (Map<String, Object>) order.get("amount");
        if (toMinor(amount) > toMinor((Map<String, Object>) order.get("amount"))) {
            order.put("status", "APPROVED");
            return new Response(422, Map.of("name", "UNPROCESSABLE_ENTITY", "details", List.of(Map.of("issue", "CAPTURE_AMOUNT_EXCEEDED"))));
        }
        order.put("captured", amount);
        ledger.record(id, toMinor(amount), (String) amount.get("currency_code"));
        Map<String, Object> captured = view(order);
        captured.put("purchase_units", List.of(Map.of("payments", Map.of("captures",
                List.of(Map.of("id", "CAP" + id, "status", "COMPLETED", "amount", amount))))));
        webhook("PAYMENT.CAPTURE.COMPLETED", Map.of(
                "id", "CAP" + id, "status", "COMPLETED", "amount", amount,
                "custom_id", String.valueOf(order.get("reference_id")),
                "supplementary_data", Map.of("related_ids", Map.of("order_id", id))));
        return new Response(201, captured);
    }

    /**
     * PayPal-style event, signed with headers {@code Sim-Transmission-Id}, {@code Sim-Transmission-Time}
     * and {@code Sim-Transmission-Sig = hex hmac(secret, id + "|" + time + "|" + body)}.
     */
    private void webhook(String eventType, Map<String, Object> resource) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "WH-" + UUID.randomUUID());
        event.put("event_type", eventType);
        event.put("create_time", java.time.Instant.now().toString());
        event.put("resource", resource);
        String body = SimHttp.json(event);
        webhooks.send(secret -> {
            String transmissionId = UUID.randomUUID().toString();
            String time = java.time.Instant.now().toString();
            String sig = java.util.HexFormat.of().formatHex(
                    SimHttp.hmacSha256(secret, transmissionId + "|" + time + "|" + body));
            return new WebhookSender.Signed(body, Map.of("Sim-Transmission-Id", transmissionId,
                    "Sim-Transmission-Time", time, "Sim-Transmission-Sig", sig));
        }, false);
    }

    private Map<String, Object> view(Map<String, Object> order) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", order.get("id"));
        view.put("status", order.get("status"));
        view.put("links", List.of(
                Map.of("rel", "self", "href", baseUrl() + ORDERS + "/" + order.get("id"), "method", "GET"),
                Map.of("rel", "approve", "href", baseUrl() + "/checkoutnow?token=" + order.get("id"), "method", "GET")));
        return view;
    }

    private static long toMinor(Map<String, Object> amount) {
        Currency currency = Currency.getInstance((String) amount.get("currency_code"));
        return new BigDecimal((String) amount.get("value"))
                .movePointRight(currency.getDefaultFractionDigits())
                .longValueExact();
    }
}
