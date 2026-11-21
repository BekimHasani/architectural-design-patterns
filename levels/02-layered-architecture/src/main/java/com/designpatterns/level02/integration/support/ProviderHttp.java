package com.designpatterns.level02.integration.support;

import com.designpatterns.level02.integration.gateway.FailureCategory;
import com.designpatterns.level02.integration.gateway.GatewayResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * HTTP plumbing shared by all payment adapters. Mappers receive a plain {@link Reply}
 * (status + body text), so they can be unit-tested with recorded JSON and no network.
 */
public final class ProviderHttp {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ProviderHttp() {
    }

    /** A provider's raw HTTP answer. */
    public record Reply(int status, String body) {
    }

    /** Sends the request and returns the answer whatever its status code. */
    public static Reply exchange(RestClient.RequestHeadersSpec<?> request) {
        return request.exchange((req, res) -> new Reply(
                res.getStatusCode().value(),
                Objects.requireNonNullElse(res.bodyTo(String.class), "")));
    }

    /**
     * Every provider call gets a deadline. Without one, a hanging provider hangs our checkout
     * thread forever. Connect timeout: the request was never sent. Read timeout: it was.
     */
    public static RestClient.Builder withTimeouts(RestClient.Builder builder, Duration connect, Duration read) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connect).build());
        factory.setReadTimeout(read);
        return builder.requestFactory(factory);
    }

    /**
     * Runs a provider call and turns transport failures into {@link GatewayResult.Failed} results.
     * THE MOST IMPORTANT DISTINCTION IN THE PROJECT is made here: "the request never left" (safe to try
     * elsewhere) versus "it left and no answer came back" (outcome unknown: the money may have moved).
     */
    public static GatewayResult guard(Supplier<GatewayResult> call) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            // Refused / connect timeout: the request never left, so nothing was charged → safe.
            // Anything else (read timeout, connection reset): the provider MAY have charged → unknown.
            return causedBy(e, ConnectException.class) || causedBy(e, HttpConnectTimeoutException.class)
                    ? new GatewayResult.Failed(FailureCategory.PROVIDER_UNAVAILABLE, "Not sent: " + e.getMessage())
                    : new GatewayResult.Failed(FailureCategory.OUTCOME_UNKNOWN, "Sent, no answer: " + e.getMessage());
        } catch (RestClientResponseException e) {
            return failure(new Reply(e.getStatusCode().value(), e.getResponseBodyAsString()));
        }
    }

    /** Maps an HTTP error status to a category. Each mapper handles its provider's expected outcomes first. */
    public static GatewayResult.Failed failure(Reply reply) {
        String detail = "HTTP " + reply.status() + ": " + reply.body();
        if (reply.status() == 429) {
            return new GatewayResult.Failed(FailureCategory.RATE_LIMITED, detail);
        }
        if (reply.status() >= 400 && reply.status() < 500) {
            return new GatewayResult.Failed(FailureCategory.INVALID_REQUEST, detail);
        }
        if (reply.status() == 503) {
            return new GatewayResult.Failed(FailureCategory.PROVIDER_UNAVAILABLE, detail);
        }
        // A 500 does NOT prove that nothing was charged.
        return new GatewayResult.Failed(FailureCategory.OUTCOME_UNKNOWN, detail);
    }

    public static <T> Optional<T> parse(String body, Class<T> type) {
        try {
            return Optional.ofNullable(JSON.readValue(body, type));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /** The provider has no record of our attempt: it was never processed, nothing was charged. */
    public static GatewayResult.Failed notProcessed() {
        return new GatewayResult.Failed(FailureCategory.NOT_PROCESSED, "Provider has no record of this attempt");
    }

    public static GatewayResult.Failed unreadable(Reply reply) {
        return new GatewayResult.Failed(FailureCategory.OUTCOME_UNKNOWN, "Unreadable response, HTTP " + reply.status());
    }

    private static boolean causedBy(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
