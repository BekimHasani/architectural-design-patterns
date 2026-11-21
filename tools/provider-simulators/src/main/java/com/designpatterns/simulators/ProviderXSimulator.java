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
 * "Provider X": a deliberately simple provider that charges directly. There is no separate
 * authorization, no capture and no cancel. It exists to prove a 4th provider can be added
 * without touching core payment logic.
 *
 * <pre>
 * POST /api/charge               {cents, ccy, ref}   X-Merchant-Token: xt_...   (same ref → same charge)
 * GET  /api/charge?ref=att_1
 * POST /api/charge/{id}/refund   {cents, ref}
 * </pre>
 * Outcome: {@code state}: paid | rejected. Errors: {@code {"oops": "..."}}.
 */
public final class ProviderXSimulator extends BaseSimulator {

    public static final int DEFAULT_PORT = 4104;
    private static final String PATH = "/api/charge";

    private final AtomicLong ids = new AtomicLong();
    private final Map<String, Map<String, Object>> charges = new LinkedHashMap<>();
    private final Map<String, Object> refundsByRef = new HashMap<>();

    private ProviderXSimulator(int port) throws IOException {
        super(port);
        route(PATH, this::handle);
    }

    public static ProviderXSimulator start(int port) throws IOException {
        return new ProviderXSimulator(port).started();
    }

    /** No customer step exists at this provider. */
    @Override
    public void completeCustomerAction(String providerRef) {
    }

    @Override
    protected synchronized void resetState() {
        charges.clear();
        refundsByRef.clear();
    }

    private void handle(HttpExchange ex) throws IOException {
        String token = ex.getRequestHeaders().getFirst("X-Merchant-Token");
        if (token == null || !token.startsWith("xt_")) {
            SimHttp.writeJson(ex, 401, Map.of("oops", "bad token"));
            return;
        }
        sawCredential(token);
        List<String> parts = SimHttp.segments(ex, PATH);
        Map<String, Object> body = SimHttp.readJson(ex);
        int status = 200;
        Object answer;
        Delivery delivery = Delivery.NORMAL;
        synchronized (this) {
            if (ex.getRequestMethod().equals("GET")) {
                String ref = SimHttp.queryParam(ex, "ref");
                answer = Map.of("charges", charges.values().stream()
                        .filter(c -> Objects.equals(c.get("ref"), ref)).toList());
            } else if (parts.isEmpty()) {
                Fault fault = faults.next();
                if (fault == Fault.HTTP_503 || fault == Fault.HTTP_500 || fault == Fault.HTTP_429) {
                    status = fault == Fault.HTTP_503 ? 503 : fault == Fault.HTTP_500 ? 500 : 429;
                    answer = Map.of("oops", "busy");
                } else {
                    answer = charge(body, fault);
                    delivery = switch (fault) {
                        case DROP_AFTER_COMMIT -> Delivery.DROP;
                        case TIMEOUT -> Delivery.HANG;
                        default -> Delivery.NORMAL;
                    };
                }
            } else if (parts.size() == 2 && parts.get(1).equals("refund")) {
                Map<String, Object> charge = charges.get(parts.get(0));
                long cents = SimHttp.asLong(body.get("cents"));
                delivery = faults.next() == Fault.DROP_AFTER_COMMIT ? Delivery.DROP : Delivery.NORMAL;
                String ref = String.valueOf(body.get("ref"));
                if (refundsByRef.containsKey(ref)) {
                    answer = refundsByRef.get(ref);
                } else if (charge == null || !"paid".equals(charge.get("state"))
                        || (long) charge.getOrDefault("refunded", 0L) + cents > (long) charge.get("cents")) {
                    status = 422;
                    answer = Map.of("oops", "cannot refund");
                } else {
                    answer = refund(charge, cents);
                    refundsByRef.put(ref, answer);
                }
            } else {
                status = 404;
                answer = Map.of("oops", "nope");
            }
        }
        respond(ex, status, answer, delivery);
    }

    private Map<String, Object> charge(Map<String, Object> body, Fault fault) {
        String ref = String.valueOf(body.get("ref"));
        for (Map<String, Object> existing : charges.values()) {
            if (ref.equals(existing.get("ref"))) {
                return existing;
            }
        }
        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("charge_id", "x_" + ids.incrementAndGet());
        charge.put("ref", ref);
        charge.put("cents", SimHttp.asLong(body.get("cents")));
        charge.put("ccy", body.get("ccy"));
        charge.put("state", fault == Fault.DECLINE ? "rejected" : "paid");
        charges.put((String) charge.get("charge_id"), charge);
        if (fault != Fault.DECLINE) {
            ledger.record((String) charge.get("charge_id"), (long) charge.get("cents"), (String) charge.get("ccy"));
        }
        return charge;
    }

    private Map<String, Object> refund(Map<String, Object> charge, long cents) {
        charge.put("refunded", (long) charge.getOrDefault("refunded", 0L) + cents);
        refundLedger.record((String) charge.get("charge_id"), cents, (String) charge.get("ccy"));
        return Map.of("refund_id", "xr_" + ids.incrementAndGet(), "state", "refunded");
    }
}
