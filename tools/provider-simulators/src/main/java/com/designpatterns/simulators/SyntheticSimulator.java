package com.designpatterns.simulators;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small providers with deliberately awkward capabilities, for Level 8. One API, one {@link Mode}:
 *
 * <pre>
 * POST /pay               {amountMinor, currency, ref}   X-Key: syn_...   (same ref → same payment)
 * GET  /pay?ref=att_1
 * POST /pay/{id}/capture  {amountMinor, ref}
 * POST /pay/{id}/cancel   {ref}
 * POST /pay/{id}/refund   {amountMinor, ref}
 * </pre>
 * {@code state}: held | paid | pending | refused | cancelled. Errors: {@code {"error": "..."}}.
 */
public final class SyntheticSimulator extends BaseSimulator {

    /** HOLD: authorize + capture + cancel, NO refunds. CHARGE: pay only. ASYNC: always pending, then refund once. */
    public enum Mode { HOLD, CHARGE, ASYNC }

    private final Mode mode;
    private final AtomicLong ids = new AtomicLong();
    private final Map<String, Map<String, Object>> payments = new LinkedHashMap<>();
    private final Map<String, Object> operationsByRef = new HashMap<>();

    private SyntheticSimulator(int port, Mode mode) throws IOException {
        super(port);
        this.mode = mode;
        route("/pay", this::handle);
    }

    public static SyntheticSimulator start(int port, Mode mode) throws IOException {
        return new SyntheticSimulator(port, mode).started();
    }

    @Override
    public void completeCustomerAction(String providerRef) {
    }

    @Override
    protected synchronized void resetState() {
        payments.clear();
        operationsByRef.clear();
    }

    /** Finishes a pending payment (ASYNC mode). */
    public synchronized void settle(String id) {
        Map<String, Object> payment = payments.get(id);
        payment.put("state", "paid");
        ledger.record(id, (long) payment.get("amountMinor"), (String) payment.get("currency"));
    }

    private void handle(HttpExchange ex) throws IOException {
        String key = ex.getRequestHeaders().getFirst("X-Key");
        if (key == null || !key.startsWith("syn_")) {
            SimHttp.writeJson(ex, 401, Map.of("error", "bad key"));
            return;
        }
        sawCredential(key);
        List<String> parts = SimHttp.segments(ex, "/pay");
        Map<String, Object> body = SimHttp.readJson(ex);
        int status = 200;
        Object answer;
        Delivery delivery = Delivery.NORMAL;
        synchronized (this) {
            if (ex.getRequestMethod().equals("GET")) {
                String ref = SimHttp.queryParam(ex, "ref");
                answer = Map.of("payments", payments.values().stream().filter(p -> Objects.equals(p.get("ref"), ref)).toList());
            } else {
                Fault fault = faults.next();
                switch (fault) {
                    case HTTP_503 -> status = 503;
                    case HTTP_500 -> status = 500;
                    case HTTP_429 -> status = 429;
                    case TIMEOUT -> delivery = Delivery.HANG;
                    case DROP_AFTER_COMMIT -> delivery = Delivery.DROP;
                    default -> { }
                }
                if (status != 200) {
                    answer = Map.of("error", "busy");
                } else if (parts.isEmpty()) {
                    answer = pay(body, fault);
                } else {
                    String ref = String.valueOf(body.get("ref"));
                    Object known = operationsByRef.get(ref);
                    Map<String, Object> result = known != null ? null : operate(parts.get(0), parts.get(1), body);
                    if (known != null) {
                        answer = known;
                    } else if (result.containsKey("error")) {
                        status = 422;
                        answer = result;
                    } else {
                        operationsByRef.put(ref, result);
                        answer = result;
                    }
                }
            }
        }
        respond(ex, status, answer, delivery);
    }

    private Map<String, Object> pay(Map<String, Object> body, Fault fault) {
        String ref = String.valueOf(body.get("ref"));
        for (Map<String, Object> existing : payments.values()) {
            if (ref.equals(existing.get("ref"))) {
                return existing;
            }
        }
        Map<String, Object> payment = new LinkedHashMap<>();
        String id = "syn_" + ids.incrementAndGet();
        payment.put("id", id);
        payment.put("ref", ref);
        payment.put("amountMinor", SimHttp.asLong(body.get("amountMinor")));
        payment.put("currency", body.get("currency"));
        String state = switch (mode) {
            case HOLD -> "held";
            case CHARGE -> "paid";
            case ASYNC -> "pending";
        };
        payment.put("state", fault == Fault.DECLINE ? "refused" : state);
        payments.put(id, payment);
        if ("paid".equals(payment.get("state"))) {
            ledger.record(id, (long) payment.get("amountMinor"), (String) payment.get("currency"));
        }
        return payment;
    }

    private Map<String, Object> operate(String id, String action, Map<String, Object> body) {
        Map<String, Object> payment = payments.get(id);
        if (payment == null) {
            return Map.of("error", "no such payment");
        }
        long total = (long) payment.get("amountMinor");
        long amount = body.containsKey("amountMinor") ? SimHttp.asLong(body.get("amountMinor")) : total;
        String currency = (String) payment.get("currency");
        switch (action) {
            case "capture" -> {
                if (mode != Mode.HOLD || !"held".equals(payment.get("state")) || amount > total) {
                    return Map.of("error", "cannot capture");
                }
                payment.put("state", "paid");
                payment.put("amountMinor", amount);
                ledger.record(id, amount, currency);
            }
            case "cancel" -> {
                if (mode != Mode.HOLD || !"held".equals(payment.get("state"))) {
                    return Map.of("error", "cannot cancel");
                }
                payment.put("state", "cancelled");
            }
            case "refund" -> {
                long refunded = (long) payment.getOrDefault("refunded", 0L);
                if (mode != Mode.ASYNC || !"paid".equals(payment.get("state")) || refunded + amount > total) {
                    return Map.of("error", "cannot refund");
                }
                payment.put("refunded", refunded + amount);
                refundLedger.record(id, amount, currency);
            }
            default -> {
                return Map.of("error", "unknown action");
            }
        }
        return Map.of("id", id, "state", "done");
    }
}
