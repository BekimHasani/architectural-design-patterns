package com.designpatterns.level09.integration.provider.stripe.webhook;

import com.designpatterns.level09.integration.support.Signatures;
import com.designpatterns.level09.integration.webhook.WebhookTranslation;
import com.designpatterns.level09.model.ReportedOutcome;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The translator is a plain class: verify + map, testable without HTTP or Spring. */
class StripeEventTranslatorTest {

    private static final String SECRET = "whsec_test";
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final String BODY = """
            {"id":"evt_42","type":"payment_intent.succeeded","created":1793275200,
             "data":{"object":{"id":"pi_7","status":"succeeded","metadata":{"attempt_id":"att_1"}}}}""";

    private final StripeEventTranslator translator = new StripeEventTranslator(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aSignedEventBecomesOneProviderEvent_keepingTheRawBodyForTheInbox() {
        WebhookTranslation result = translator.translate(Map.of("sim-signature", signature(BODY)), BODY);

        assertThat(result).isInstanceOfSatisfying(WebhookTranslation.Events.class, events -> {
            assertThat(events.events()).hasSize(1);
            assertThat(events.events().get(0).event().outcome()).isEqualTo(ReportedOutcome.CAPTURED);
            assertThat(events.events().get(0).rawPayload()).isEqualTo(BODY);
            assertThat(events.acknowledgement()).isNull();
        });
    }

    @Test
    void aMissingOrWrongSignatureIsRejected() {
        assertThat(translator.translate(Map.of(), BODY)).isInstanceOf(WebhookTranslation.Rejected.class);
        assertThat(translator.translate(Map.of("sim-signature", signature(BODY)), BODY.replace("succeeded", "failed")))
                .isInstanceOf(WebhookTranslation.Rejected.class);
    }

    @Test
    void signedGarbageIsUnreadable() {
        assertThat(translator.translate(Map.of("sim-signature", signature("[]")), "[]"))
                .isInstanceOf(WebhookTranslation.Unreadable.class);
    }

    private static String signature(String body) {
        long t = NOW.getEpochSecond();
        return "t=" + t + ",v1=" + HexFormat.of().formatHex(Signatures.hmacSha256(SECRET, t + "." + body));
    }
}
