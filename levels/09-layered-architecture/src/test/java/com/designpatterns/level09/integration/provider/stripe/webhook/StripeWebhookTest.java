package com.designpatterns.level09.integration.provider.stripe.webhook;

import com.designpatterns.level09.integration.support.Signatures;
import com.designpatterns.level09.integration.webhook.ProviderEvent;
import com.designpatterns.level09.model.ReportedOutcome;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class StripeWebhookTest {

    private static final String SECRET = "whsec_test";
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final String BODY = """
            {"id":"evt_42","type":"payment_intent.succeeded","created":1793275200,
             "data":{"object":{"id":"pi_7","status":"succeeded","metadata":{"attempt_id":"att_1"}}}}""";

    private final StripeWebhookVerifier verifier = new StripeWebhookVerifier(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsAValidSignature() {
        assertThat(verifier.verify(header(SECRET, NOW, BODY), BODY)).isTrue();
    }

    @Test
    void rejectsATamperedBody() {
        assertThat(verifier.verify(header(SECRET, NOW, BODY), BODY.replace("succeeded", "failed"))).isFalse();
    }

    @Test
    void rejectsTheWrongSecret() {
        assertThat(verifier.verify(header("attacker", NOW, BODY), BODY)).isFalse();
    }

    @Test
    void rejectsAReplayOfAnOldWebhook() {
        assertThat(verifier.verify(header(SECRET, NOW.minusSeconds(600), BODY), BODY)).isFalse();
    }

    @Test
    void rejectsGarbage() {
        assertThat(verifier.verify(null, BODY)).isFalse();
        assertThat(verifier.verify("t=abc,v1=zz", BODY)).isFalse();
    }

    @Test
    void mapsToTheSharedProviderEvent() {
        ProviderEvent event = StripeWebhookMapper.toEvent(BODY).orElseThrow();

        assertThat(event.eventId()).isEqualTo("evt_42");
        assertThat(event.attemptId()).as("our reference, from metadata").isEqualTo("att_1");
        assertThat(event.providerRef()).isEqualTo("pi_7");
        assertThat(event.outcome()).isEqualTo(ReportedOutcome.CAPTURED);
    }

    @Test
    void mapsEveryEventTypeWeCareAbout() {
        assertThat(outcomeOf("payment_intent.processing")).isEqualTo(ReportedOutcome.PROCESSING);
        assertThat(outcomeOf("payment_intent.amount_capturable_updated")).isEqualTo(ReportedOutcome.AUTHORIZED);
        assertThat(outcomeOf("payment_intent.payment_failed")).isEqualTo(ReportedOutcome.DECLINED);
        assertThat(outcomeOf("customer.created")).as("irrelevant").isNull();
    }

    private static ReportedOutcome outcomeOf(String type) {
        return StripeWebhookMapper.toEvent(BODY.replace("payment_intent.succeeded", type)).orElseThrow().outcome();
    }

    private static String header(String secret, Instant at, String body) {
        long t = at.getEpochSecond();
        return "t=" + t + ",v1=" + HexFormat.of().formatHex(Signatures.hmacSha256(secret, t + "." + body));
    }
}
