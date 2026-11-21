package com.designpatterns.simulators;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Plumbing shared by all simulators: the HTTP server, fault injection, the ledger and the
 * {@code /sim/*} control endpoints. Subclasses only add their provider-shaped API.
 */
public abstract class BaseSimulator implements AutoCloseable {

    protected final FaultInjector faults = new FaultInjector();
    protected final Ledger ledger = new Ledger();
    protected final Ledger refundLedger = new Ledger();
    private final List<String> credentialsSeen = new CopyOnWriteArrayList<>();
    protected final WebhookSender webhooks = new WebhookSender();
    private final HttpServer server;

    protected BaseSimulator(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        route("/sim/faults", ex -> {
            Map<String, Object> body = SimHttp.readJson(ex);
            int count = body.containsKey("count") ? (int) SimHttp.asLong(body.get("count")) : 0;
            injectFault(Fault.parse(String.valueOf(body.getOrDefault("mode", "none"))), count);
            SimHttp.writeJson(ex, 200, Map.of("ok", true));
        });
        route("/sim/ledger", ex -> SimHttp.writeJson(ex, 200, ledger()));
        route("/sim/reset", ex -> {
            reset();
            SimHttp.writeJson(ex, 200, Map.of("ok", true));
        });
    }

    protected final void route(String pathPrefix, HttpHandler handler) {
        server.createContext(pathPrefix, handler);
    }

    protected final <T extends BaseSimulator> T started() {
        server.start();
        @SuppressWarnings("unchecked") T self = (T) this;
        return self;
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** The next {@code count} business requests fail with {@code fault} (0 = until reset). */
    public void injectFault(Fault fault, int count) {
        faults.set(fault, count);
    }

    public List<Ledger.Entry> ledger() {
        return ledger.entries();
    }

    /** What was really refunded. */
    public List<Ledger.Entry> refunds() {
        return refundLedger.entries();
    }

    /** Every credential that was used, in order: proves per-merchant credentials reach the provider. */
    public List<String> credentialsSeen() {
        return List.copyOf(credentialsSeen);
    }

    protected final void sawCredential(String credential) {
        credentialsSeen.add(credential);
    }

    /** Real providers don't remember failures they never processed: a retry must really retry. */
    protected static boolean worthRemembering(int status) {
        return status < 500 && status != 429;
    }

    /** Simulates the customer finishing 3-D Secure / approving the payment in the browser. */
    public abstract void completeCustomerAction(String providerRef);

    public void reset() {
        faults.reset();
        ledger.clear();
        refundLedger.clear();
        credentialsSeen.clear();
        webhooks.reset();
        resetState();
    }

    /** From now on, events are POSTed to {@code url}, signed with {@code secret}. */
    public void sendWebhooksTo(String url, String secret) {
        webhooks.configure(url, secret);
    }

    public void injectWebhookFault(WebhookSender.Mode mode) {
        webhooks.mode(mode);
    }

    public List<WebhookSender.Delivery> webhookDeliveries() {
        return webhooks.deliveries();
    }

    protected abstract void resetState();

    /** How a processed request's answer reaches (or doesn't reach) the client. */
    protected enum Delivery {
        NORMAL,
        /** Held back far longer than any sane client timeout ({@link Fault#TIMEOUT}). */
        HANG,
        /** Connection cut without any answer ({@link Fault#DROP_AFTER_COMMIT}). */
        DROP
    }

    protected static void respond(HttpExchange ex, int status, Object body, Delivery delivery) throws IOException {
        switch (delivery) {
            case HANG -> {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                SimHttp.writeJson(ex, status, body);
            }
            case DROP -> ex.close(); // no status line, no body: the client sees a broken connection
            case NORMAL -> SimHttp.writeJson(ex, status, body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
