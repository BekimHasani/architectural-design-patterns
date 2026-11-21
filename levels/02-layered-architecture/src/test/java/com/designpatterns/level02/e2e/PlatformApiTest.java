package com.designpatterns.level02.e2e;

import com.designpatterns.simulators.BaseSimulator;
import com.designpatterns.simulators.Fault;
import com.designpatterns.simulators.SimulatorsMain;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole platform over HTTP, against the simulators' ledgers. Merchant "acme" is routed to
 * Stripe; "globex" follows the currency rules (EUR → Adyen, GBP → PayPal).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlatformApiTest {

    static final Simulators SIMS = new Simulators();

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry registry) {
        SIMS.register(registry);
    }

    @AfterAll
    static void stop() {
        SIMS.all().forEach(BaseSimulator::close);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate http;

    @BeforeEach
    void reset() {
        SIMS.all().forEach(BaseSimulator::reset);
        String app = "http://localhost:" + port;
        SIMS.stripe.sendWebhooksTo(app + "/webhooks/stripe", SimulatorsMain.STRIPE_WEBHOOK_SECRET);
        SIMS.paypal.sendWebhooksTo(app + "/webhooks/paypal", SimulatorsMain.PAYPAL_WEBHOOK_SECRET);
        SIMS.adyen.sendWebhooksTo(app + "/webhooks/adyen", SimulatorsMain.ADYEN_HMAC_KEY);
    }

    @Test
    void stripe_authorize100_capture60_refund30_refund31Refused_refund30() {
        String id = authorize("acme", "USD", 10000).get("paymentId").asText();

        lifecycle("acme", id, SIMS.stripe);

        assertThat(history("acme", id)).extracting(e -> e.get("type").asText()).contains(
                "PaymentCreated", "AttemptStarted", "PaymentAuthorized", "CAPTURERequested", "PaymentCaptured",
                "REFUNDRequested", "PaymentRefunded");
    }

    @Test
    void adyen_sameLifecycle_sameApi() {
        lifecycle("globex", authorize("globex", "EUR", 10000).get("paymentId").asText(), SIMS.adyen);
    }

    @Test
    void paypal_sameLifecycle_afterTheBuyerApproves() {
        JsonNode started = authorize("globex", "GBP", 10000);
        assertThat(started.get("status").asText()).isEqualTo("action_required");
        String id = started.get("paymentId").asText();
        String redirect = started.get("redirectUrl").asText();
        SIMS.paypal.completeCustomerAction(redirect.substring(redirect.indexOf("token=") + 6));

        assertThat(post("globex", "/payments/" + id + "/complete", null, null).getStatusCode().value()).isEqualTo(200);

        lifecycle("globex", id, SIMS.paypal);
    }

    private void lifecycle(String merchant, String id, BaseSimulator sim) {
        JsonNode captured = body(post(merchant, "/payments/" + id + "/capture", "{\"amountMinor\":6000}", key()));
        assertThat(captured.get("status").asText()).isEqualTo("captured");
        assertThat(captured.get("captured").asLong()).isEqualTo(6000);
        assertThat(sim.ledger()).singleElement().satisfies(e -> assertThat(e.amountMinor()).isEqualTo(6000));

        JsonNode refunded = body(post(merchant, "/payments/" + id + "/refund", "{\"amountMinor\":3000}", key()));
        assertThat(refunded.get("refundable").asLong()).isEqualTo(3000);

        assertThat(post(merchant, "/payments/" + id + "/refund", "{\"amountMinor\":3001}", key()).getStatusCode().value())
                .as("the aggregate says no, before any provider call").isEqualTo(422);
        assertThat(sim.refunds()).hasSize(1);

        JsonNode done = body(post(merchant, "/payments/" + id + "/refund", "{\"amountMinor\":3000}", key()));
        assertThat(done.get("status").asText()).isEqualTo("refunded");
        assertThat(sim.refunds()).hasSize(2);
    }

    @Test
    void cancelReleasesTheFunds() {
        String id = authorize("acme", "USD", 10000).get("paymentId").asText();

        assertThat(body(post("acme", "/payments/" + id + "/cancel", null, key())).get("status").asText()).isEqualTo("cancelled");
        assertThat(post("acme", "/payments/" + id + "/capture", null, key()).getStatusCode().value()).isEqualTo(409);
        assertThat(SIMS.stripe.ledger()).isEmpty();
    }

    @Test
    void eachMerchantsOwnCredentialsReachTheProvider() {
        authorize("acme", "EUR", 1000);    // acme is routed to stripe
        authorize("globex", "USD", 1000);  // USD → stripe

        assertThat(SIMS.stripe.credentialsSeen()).contains("sk_test_acme", "sk_test_globex");
    }

    @Test
    void aMerchantCannotSeeOrTouchAnotherMerchantsPayment() {
        String id = authorize("acme", "USD", 10000).get("paymentId").asText();

        assertThat(get("globex", "/payments/" + id).getStatusCode().value()).isEqualTo(404);
        assertThat(get("globex", "/payments/" + id + "/history").getStatusCode().value()).isEqualTo(404);
        assertThat(post("globex", "/payments/" + id + "/capture", null, key()).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void anUnknownMerchantIsRefused() {
        assertThat(post("nobody", "/payments", cart("USD", 100), key()).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void capturingTwiceWithTheSameKeyMovesTheMoneyOnce() {
        String id = authorize("acme", "USD", 10000).get("paymentId").asText();
        String key = key();

        ResponseEntity<String> first = post("acme", "/payments/" + id + "/capture", null, key);
        ResponseEntity<String> again = post("acme", "/payments/" + id + "/capture", null, key);

        assertThat(again.getBody()).isEqualTo(first.getBody());
        assertThat(again.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(SIMS.stripe.ledger()).hasSize(1);
    }

    @Test
    void lostCaptureAnswer_reconciliationResendsIt_moneyMovesOnce() {
        String id = authorize("acme", "USD", 10000).get("paymentId").asText();
        SIMS.stripe.injectFault(Fault.DROP_AFTER_COMMIT, 1);

        ResponseEntity<String> response = post("acme", "/payments/" + id + "/capture", null, key());
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(body(response).get("status").asText()).isEqualTo("authorized");
        assertThat(SIMS.stripe.ledger()).as("it did happen").hasSize(1);

        http.postForEntity("/admin/reconcile", null, JsonNode.class);

        assertThat(body(get("acme", "/payments/" + id)).get("status").asText()).isEqualTo("captured");
        assertThat(SIMS.stripe.ledger()).as("once").hasSize(1);
        assertThat(history("acme", id)).anyMatch(e -> e.get("actor").asText().equals("reconciliation"));
    }

    @RepeatedTest(100) // the race it guards against showed up in about 3% of runs
    void refundsRacingEachOtherCannotExceedWhatWasCaptured() throws Exception {
        String id = authorize("acme", "USD", 10000).get("paymentId").asText();
        post("acme", "/payments/" + id + "/capture", null, key());
        List<Callable<ResponseEntity<String>>> refunds = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            refunds.add(() -> post("acme", "/payments/" + id + "/refund", "{\"amountMinor\":4000}", key()));
        }

        List<ResponseEntity<String>> responses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<ResponseEntity<String>> f : pool.invokeAll(refunds)) {
                responses.add(f.get());
            }
        }

        assertThat(SIMS.stripe.refunds().stream().mapToLong(e -> e.amountMinor()).sum()).isLessThanOrEqualTo(10000);
        assertThat(responses).extracting(r -> r.getStatusCode().value()).allMatch(s -> s == 200 || s == 409 || s == 422);
    }

    @Test
    void fallbackStillWorks_andLaterOperationsFollowTheProviderThatTookThePayment() {
        SIMS.stripe.injectFault(Fault.HTTP_503, 3);

        JsonNode payment = authorize("acme", "USD", 10000);
        assertThat(payment.get("provider").asText()).isEqualTo("adyen");

        post("acme", "/payments/" + payment.get("paymentId").asText() + "/capture", null, key());
        assertThat(SIMS.adyen.ledger()).hasSize(1);
        assertThat(SIMS.stripe.ledger()).isEmpty();
    }

    @Test
    void theAuditTrailSaysWhoDidWhat_includingTheProvider() {
        String id = authorize("acme", "USD", 10000).get("paymentId").asText(); // the webhook beats the API answer

        assertThat(history("acme", id)).anyMatch(e -> e.get("type").asText().equals("PaymentAuthorized")
                && e.get("actor").asText().equals("provider:stripe"));
        assertThat(history("acme", id).get(0).get("actor").asText()).isEqualTo("merchant:acme");
    }

    // ---- helpers ------------------------------------------------------------------------------

    private JsonNode authorize(String merchant, String currency, long amount) {
        return body(post(merchant, "/payments", cart(currency, amount), key()));
    }

    private static String cart(String currency, long amount) {
        return "{\"customerId\":\"c-1\",\"currency\":\"" + currency + "\",\"amountMinor\":" + amount + "}";
    }

    private ResponseEntity<String> post(String merchant, String path, String body, String key) {
        RequestEntity.BodyBuilder request = RequestEntity.post(URI.create(path)).header("X-Merchant-Id", merchant)
                .contentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return http.exchange(request.body(body == null ? "" : body), String.class);
    }

    private ResponseEntity<String> get(String merchant, String path) {
        return http.exchange(RequestEntity.method(HttpMethod.GET, URI.create(path)).header("X-Merchant-Id", merchant).build(), String.class);
    }

    private List<JsonNode> history(String merchant, String id) {
        List<JsonNode> entries = new ArrayList<>();
        body(get(merchant, "/payments/" + id + "/history")).forEach(entries::add);
        return entries;
    }

    private static JsonNode body(ResponseEntity<String> response) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException(response.toString(), e);
        }
    }

    private static String key() {
        return "idem_" + UUID.randomUUID();
    }
}
