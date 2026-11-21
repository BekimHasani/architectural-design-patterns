package com.designpatterns.level03.integration.support;

import com.designpatterns.level03.integration.gateway.GatewayResult;
import com.designpatterns.level03.integration.gateway.GatewayResult.FailureKind;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** HTTP plumbing shared by the adapters. */
public final class ProviderHttp {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ProviderHttp() {
    }

    /** A provider's raw HTTP answer. */
    public record Reply(int status, String body) {
    }

    public static RestClient restClient(String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        return RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    /** Sends the request and returns the answer whatever its status code. */
    public static Reply exchange(RestClient.RequestHeadersSpec<?> request) {
        return request.exchange((req, res) -> new Reply(
                res.getStatusCode().value(),
                Objects.requireNonNullElse(res.bodyTo(String.class), "")));
    }

    /**
     * Runs a provider call and turns transport failures into results. "Never sent" (connection refused,
     * connect timeout) means nothing was charged. Anything else (read timeout, connection cut) means the
     * provider may have charged.
     */
    public static GatewayResult guard(Supplier<GatewayResult> call) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            return causedBy(e, ConnectException.class) || causedBy(e, HttpConnectTimeoutException.class)
                    ? new GatewayResult.Failed(FailureKind.NOTHING_CHARGED, "Not sent: " + e.getMessage())
                    : new GatewayResult.Failed(FailureKind.OUTCOME_UNKNOWN, "Sent, no answer: " + e.getMessage());
        }
    }

    /** An HTTP error status. A 5xx other than 503 does NOT prove that nothing was charged. */
    public static GatewayResult.Failed failure(Reply reply) {
        String detail = "HTTP " + reply.status() + ": " + reply.body();
        boolean rejectedBeforeProcessing = reply.status() / 100 == 4 || reply.status() == 503;
        return new GatewayResult.Failed(
                rejectedBeforeProcessing ? FailureKind.NOTHING_CHARGED : FailureKind.OUTCOME_UNKNOWN, detail);
    }

    public static GatewayResult.Failed unreadable(Reply reply) {
        return new GatewayResult.Failed(FailureKind.OUTCOME_UNKNOWN, "Unreadable response, HTTP " + reply.status());
    }

    public static <T> Optional<T> parse(String body, Class<T> type) {
        try {
            return Optional.ofNullable(JSON.readValue(body, type));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
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
