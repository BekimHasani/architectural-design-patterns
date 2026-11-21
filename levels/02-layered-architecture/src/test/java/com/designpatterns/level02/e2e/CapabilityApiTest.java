package com.designpatterns.level02.e2e;

import com.designpatterns.simulators.BaseSimulator;
import com.designpatterns.simulators.SyntheticSimulator;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three awkward providers behind one API. hooli → holdonly (no refunds, but also has Stripe),
 * pied-piper → chargeonly, vandelay → asyncpay (EUR + SEK, refunds once in full).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CapabilityApiTest {

    static final Simulators SIMS = new Simulators();
    static final SyntheticSimulator HOLD = start(SyntheticSimulator.Mode.HOLD);
    static final SyntheticSimulator CHARGE = start(SyntheticSimulator.Mode.CHARGE);
    static final SyntheticSimulator ASYNC = start(SyntheticSimulator.Mode.ASYNC);

    private static SyntheticSimulator start(SyntheticSimulator.Mode mode) {
        try {
            return SyntheticSimulator.start(0, mode);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry registry) {
        SIMS.register(registry);
        registry.add("payment.base-urls.holdonly", HOLD::baseUrl);
        registry.add("payment.base-urls.chargeonly", CHARGE::baseUrl);
        registry.add("payment.base-urls.asyncpay", ASYNC::baseUrl);
    }

    @AfterAll
    static void stop() {
        SIMS.all().forEach(BaseSimulator::close);
        HOLD.close();
        CHARGE.close();
        ASYNC.close();
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void reset() {
        SIMS.all().forEach(BaseSimulator::reset);
        HOLD.reset();
        CHARGE.reset();
        ASYNC.reset();
    }

    @Test
    void noRefundProvider_aRefundIsRefusedClearly_beforeAnyCall() {
        JsonNode payment = pay("hooli", "USD", "\"capture\":\"automatic\"").getBody();
        assertThat(payment.get("provider").asText()).isEqualTo("holdonly");
        assertThat(payment.get("status").asText()).isEqualTo("captured");
        assertThat(payment.get("allowedActions")).isEmpty();

        ResponseEntity<JsonNode> refund = post("hooli", "/payments/" + payment.get("paymentId").asText() + "/refund", "");

        assertThat(refund.getStatusCode().value()).isEqualTo(422);
        assertThat(refund.getBody().get("detail").asText()).contains("does not support refund");
        assertThat(HOLD.refunds()).isEmpty();
    }

    @Test
    void askingForRefundsUpFront_routesAroundTheProviderThatCantDoThem() {
        JsonNode payment = pay("hooli", "USD", "\"refundsRequired\":true").getBody();

        assertThat(payment.get("provider").asText()).as("the merchant's rule said holdonly").isEqualTo("stripe");
    }

    @Test
    void automaticOnAnAuthCaptureProvider_capturesByItself() {
        JsonNode payment = pay("hooli", "USD", "\"capture\":\"automatic\"").getBody();

        assertThat(payment.get("captured").asLong()).isEqualTo(10000);
        assertThat(HOLD.ledger()).hasSize(1);
    }

    @Test
    void chargeOnlyProvider_cantHoldFunds_butCanTakeThem() {
        assertThat(pay("pied-piper", "USD", "\"capture\":\"manual\"").getStatusCode().value()).isEqualTo(422);
        assertThat(CHARGE.ledger()).isEmpty();

        assertThat(pay("pied-piper", "USD", "\"capture\":\"automatic\"").getBody().get("status").asText()).isEqualTo("captured");
    }

    @Test
    void asyncProvider_currencyLimits_laterResult_refundOnceInFull() {
        assertThat(pay("vandelay", "USD", "\"capture\":\"automatic\"").getStatusCode().value())
                .as("EUR and SEK only").isEqualTo(422);

        JsonNode payment = pay("vandelay", "EUR", "\"capture\":\"automatic\"").getBody();
        String id = payment.get("paymentId").asText();
        assertThat(payment.get("status").asText()).isEqualTo("processing");

        ASYNC.settle(jdbc.sql("SELECT provider_ref FROM payments WHERE id = ?").param(id).query(String.class).single());
        http.postForEntity("/admin/reconcile", null, JsonNode.class);
        assertThat(http.exchange(RequestEntity.get(URI.create("/payments/" + id)).header("X-Merchant-Id", "vandelay").build(),
                JsonNode.class).getBody().get("status").asText()).isEqualTo("captured");

        assertThat(post("vandelay", "/payments/" + id + "/refund", "{\"amountMinor\":100}").getStatusCode().value())
                .as("full refunds only").isEqualTo(422);
        assertThat(post("vandelay", "/payments/" + id + "/refund", "").getBody().get("status").asText()).isEqualTo("refunded");
    }

    private ResponseEntity<JsonNode> pay(String merchant, String currency, String extra) {
        return post(merchant, "/payments", "{\"customerId\":\"c-1\",\"currency\":\"" + currency + "\",\"amountMinor\":10000," + extra + "}");
    }

    private ResponseEntity<JsonNode> post(String merchant, String path, String body) {
        return http.exchange(RequestEntity.post(URI.create(path)).header("X-Merchant-Id", merchant)
                .header("Idempotency-Key", "idem_" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).body(body), JsonNode.class);
    }
}
