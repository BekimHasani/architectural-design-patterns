package com.designpatterns.level08.providers.paypal.webhook;

import com.designpatterns.level08.payments.application.events.RecordProviderPaymentEvent;
import com.designpatterns.level08.payments.domain.ReportedOutcome;
import com.designpatterns.level08.providers.support.Signatures;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class PayPalWebhookTest {

    private static final String SECRET = "paypal_test";
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final String BODY = """
            {"id":"WH-1","event_type":"PAYMENT.CAPTURE.COMPLETED","create_time":"2026-10-29T12:00:00Z",
             "resource":{"id":"CAPPP1","status":"COMPLETED","custom_id":"att_1",
                         "supplementary_data":{"related_ids":{"order_id":"PP1"}}}}""";

    private final PayPalWebhookVerifier verifier = new PayPalWebhookVerifier(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void verifiesTheTransmissionSignature() {
        String time = NOW.toString();
        String sig = HexFormat.of().formatHex(Signatures.hmacSha256(SECRET, "tx-1|" + time + "|" + BODY));

        assertThat(verifier.verify("tx-1", time, sig, BODY)).isTrue();
        assertThat(verifier.verify("tx-2", time, sig, BODY)).as("signature bound to the transmission id").isFalse();
        assertThat(verifier.verify("tx-1", time, sig, BODY + " ")).as("tampered").isFalse();
        assertThat(verifier.verify("tx-1", NOW.minusSeconds(3600).toString(), sig, BODY)).as("old").isFalse();
    }

    @Test
    void captureCompletedMeansSucceeded() {
        RecordProviderPaymentEvent event = PayPalWebhookMapper.toCommand(BODY).orElseThrow();

        assertThat(event.attemptId()).isEqualTo("att_1");
        assertThat(event.providerRef()).as("PayPal's order id").isEqualTo("PP1");
        assertThat(event.outcome()).isEqualTo(ReportedOutcome.CAPTURED);
    }

    @Test
    void orderApprovedIsAcknowledgedButIrrelevant() {
        assertThat(PayPalWebhookMapper.toCommand(BODY.replace("PAYMENT.CAPTURE.COMPLETED", "CHECKOUT.ORDER.APPROVED"))
                .orElseThrow().outcome()).isNull();
    }
}
