package com.designpatterns.level09.integration.support;

import com.designpatterns.level09.integration.gateway.AuthorizeRequest;
import com.designpatterns.level09.integration.gateway.FailureCategory;
import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.integration.gateway.OperationRequest;
import com.designpatterns.level09.integration.gateway.ProviderCredentials;
import com.designpatterns.level09.model.DeclineReason;

import com.designpatterns.level09.integration.support.ProviderHttp.Reply;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static com.designpatterns.level09.integration.support.ProviderHttp.exchange;
import static com.designpatterns.level09.integration.support.ProviderHttp.guard;

/**
 * The small "synthetic" providers share one API, so they share this client, the way real adapters
 * share a vendor SDK. What each one can DO is decided by which interfaces its gateway implements.
 */
public final class SyntheticClient {

    private record Payment(String id, String state) {
    }

    private record PaymentList(List<Payment> payments) {
    }

    private final RestClient client;

    public SyntheticClient(RestClient.Builder http, String baseUrl) {
        this.client = http.baseUrl(baseUrl).build();
    }

    public GatewayResult pay(AuthorizeRequest r) {
        return guard(() -> toResult(exchange(client.post().uri("/pay")
                .header("X-Key", r.credentials().require("key"))
                .body(Map.of("amountMinor", r.amount().amountMinor(), "currency", r.amount().currency().getCurrencyCode(),
                        "ref", r.attemptId())))));
    }

    /** @param action capture, cancel or refund */
    public GatewayResult operate(String action, OperationRequest r) {
        Map<String, Object> body = r.amount() == null ? Map.of("ref", r.operationId())
                : Map.of("ref", r.operationId(), "amountMinor", r.amount().amountMinor());
        return guard(() -> {
            Reply reply = exchange(client.post().uri("/pay/{id}/{action}", r.providerRef(), action)
                    .header("X-Key", r.credentials().require("key"))
                    .body(body));
            return reply.status() == 200 ? new GatewayResult.Done(r.providerRef()) : ProviderHttp.failure(reply);
        });
    }

    public GatewayResult status(String attemptId, ProviderCredentials credentials) {
        return guard(() -> {
            Reply reply = exchange(client.get().uri(b -> b.path("/pay").queryParam("ref", attemptId).build())
                    .header("X-Key", credentials.require("key")));
            if (reply.status() != 200) {
                return ProviderHttp.failure(reply);
            }
            return ProviderHttp.parse(reply.body(), PaymentList.class)
                    .map(found -> found.payments() == null || found.payments().isEmpty()
                            ? ProviderHttp.notProcessed()
                            : fromPayment(found.payments().get(0)))
                    .orElseGet(() -> ProviderHttp.unreadable(reply));
        });
    }

    private static GatewayResult toResult(Reply reply) {
        if (reply.status() != 200) {
            return ProviderHttp.failure(reply);
        }
        return ProviderHttp.parse(reply.body(), Payment.class).map(SyntheticClient::fromPayment)
                .orElseGet(() -> ProviderHttp.unreadable(reply));
    }

    private static GatewayResult fromPayment(Payment p) {
        return switch (p.state()) {
            case "held" -> new GatewayResult.Authorized(p.id());
            case "paid" -> new GatewayResult.Charged(p.id());
            case "pending" -> new GatewayResult.Processing(p.id());
            case "refused" -> new GatewayResult.Declined(DeclineReason.GENERIC);
            default -> new GatewayResult.Failed(FailureCategory.OUTCOME_UNKNOWN, "Unexpected state: " + p.state());
        };
    }
}
