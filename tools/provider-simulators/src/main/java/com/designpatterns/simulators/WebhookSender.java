package com.designpatterns.simulators;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Posts signed webhooks to the app, synchronously, so tests are deterministic. Each simulator
 * signs in its own format (a {@link Signer}); this class only handles delivery and delivery faults.
 */
public final class WebhookSender {

    /** Webhook delivery faults, independent from API faults. Sticky until {@link BaseSimulator#reset()}. */
    public enum Mode {
        NONE,
        /** Every webhook is delivered twice (same event id). */
        DUPLICATE,
        /** "Intermediate" events (e.g. processing) are held back and delivered AFTER the final one. */
        OUT_OF_ORDER,
        /** Signed with the wrong secret, as a forger would. */
        BAD_SIGNATURE,
        /** Never delivered. Only polling (reconciliation) can find the result. */
        LOST
    }

    /** Builds the request for one event, signed with the given secret. */
    @FunctionalInterface
    public interface Signer {
        Signed sign(String secret);
    }

    public record Signed(String body, Map<String, String> headers) {
    }

    /** What the app answered, for tests (e.g. Adyen's required "[accepted]"). */
    public record Delivery(int status, String body) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final List<Signer> held = new ArrayList<>();
    private final List<Delivery> deliveries = new ArrayList<>();
    private String url;
    private String secret;
    private Mode mode = Mode.NONE;

    synchronized void configure(String url, String secret) {
        this.url = url;
        this.secret = secret;
    }

    synchronized void mode(Mode mode) {
        this.mode = mode;
    }

    synchronized void reset() {
        mode = Mode.NONE;
        held.clear();
        deliveries.clear();
    }

    synchronized List<Delivery> deliveries() {
        return List.copyOf(deliveries);
    }

    /** @param intermediate true for non-final events (e.g. "processing"), which OUT_OF_ORDER holds back */
    synchronized void send(Signer event, boolean intermediate) {
        if (url == null || mode == Mode.LOST) {
            return;
        }
        if (mode == Mode.OUT_OF_ORDER && intermediate) {
            held.add(event);
            return;
        }
        post(event);
        if (mode == Mode.DUPLICATE) {
            post(event);
        }
        if (mode == Mode.OUT_OF_ORDER) {
            held.forEach(this::post); // the stale event arrives last
            held.clear();
        }
    }

    private void post(Signer event) {
        Signed signed = event.sign(mode == Mode.BAD_SIGNATURE ? "forged-" + secret : secret);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(signed.body()));
        signed.headers().forEach(request::header);
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            deliveries.add(new Delivery(response.statusCode(), response.body()));
        } catch (Exception e) {
            deliveries.add(new Delivery(-1, e.toString())); // app down: a real provider would retry later
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
