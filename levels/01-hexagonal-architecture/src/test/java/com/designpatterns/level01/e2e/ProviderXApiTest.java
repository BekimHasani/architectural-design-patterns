package com.designpatterns.level01.e2e;

import com.designpatterns.simulators.BaseSimulator;
import com.designpatterns.simulators.ProviderXSimulator;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A 4th provider behind the same API. "authorize" means "charge": the answer is already captured. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProviderXApiTest {

    static final Simulators SIMS = new Simulators();
    static final ProviderXSimulator X = start();

    private static ProviderXSimulator start() {
        try {
            return ProviderXSimulator.start(0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry registry) {
        SIMS.register(registry);
        registry.add("payment.base-urls.providerx", X::baseUrl);
    }

    @AfterAll
    static void stop() {
        SIMS.all().forEach(BaseSimulator::close);
        X.close();
    }

    @Autowired
    TestRestTemplate http;

    @Test
    void chargesDirectly_cannotBeCaptured_refundsLikeAnyOther() {
        JsonNode payment = post("/payments", "{\"customerId\":\"c-1\",\"currency\":\"EUR\",\"amountMinor\":10000,\"capture\":\"automatic\"}").getBody();
        String id = payment.get("paymentId").asText();

        assertThat(payment.get("provider").asText()).isEqualTo("providerx");
        assertThat(payment.get("status").asText()).isEqualTo("captured");
        assertThat(payment.get("captured").asLong()).isEqualTo(10000);
        assertThat(X.ledger()).hasSize(1);
        assertThat(X.credentialsSeen()).containsOnly("xt_initech");

        assertThat(payment.get("allowedActions")).extracting(JsonNode::asText).containsExactly("refund");
        assertThat(post("/payments/" + id + "/capture", "").getStatusCode().value())
                .as("Provider X has no Capturer: refused from its descriptor").isEqualTo(422);

        JsonNode refunded = post("/payments/" + id + "/refund", "{\"amountMinor\":4000}").getBody();
        assertThat(refunded.get("refundable").asLong()).isEqualTo(6000);
        assertThat(X.refunds()).singleElement().satisfies(e -> assertThat(e.amountMinor()).isEqualTo(4000));
    }

    @Test
    void holdingFundsIsImpossibleForThisMerchant_saidBeforeAnyProviderIsCalled() {
        ResponseEntity<JsonNode> response = post("/payments", "{\"customerId\":\"c-1\",\"currency\":\"EUR\",\"amountMinor\":100}");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    private ResponseEntity<JsonNode> post(String path, String body) {
        return http.exchange(RequestEntity.post(URI.create(path)).header("X-Merchant-Id", "initech")
                .header("Idempotency-Key", "idem_" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).body(body), JsonNode.class);
    }
}
