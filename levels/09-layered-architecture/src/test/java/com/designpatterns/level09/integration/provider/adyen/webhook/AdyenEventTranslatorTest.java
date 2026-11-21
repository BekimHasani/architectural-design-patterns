package com.designpatterns.level09.integration.provider.adyen.webhook;

import com.designpatterns.level09.integration.support.Signatures;
import com.designpatterns.level09.integration.webhook.WebhookTranslation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdyenEventTranslatorTest {

    private static final String KEY = "adyen_test";

    private final AdyenEventTranslator translator = new AdyenEventTranslator(KEY, new ObjectMapper());

    @Test
    void aBatchBecomesOneEventPerItem_andAdyenGetsItsLiteralAcknowledgement() {
        WebhookTranslation result = translator.translate(Map.of(), batch(item("psp1", "att_1", "true", KEY),
                item("psp2", "att_2", "false", KEY)));

        assertThat(result).isInstanceOfSatisfying(WebhookTranslation.Events.class, events -> {
            assertThat(events.events()).hasSize(2);
            assertThat(events.acknowledgement()).isEqualTo("[accepted]");
        });
    }

    @Test
    void oneForgedItemRejectsTheWholeBatch() {
        assertThat(translator.translate(Map.of(), batch(item("psp1", "att_1", "true", KEY),
                item("psp2", "att_2", "true", "attacker")))).isInstanceOf(WebhookTranslation.Rejected.class);
    }

    @Test
    void notABatchIsUnreadable() {
        assertThat(translator.translate(Map.of(), "{\"hello\":1}")).isInstanceOf(WebhookTranslation.Unreadable.class);
    }

    private static String batch(String... items) {
        return "{\"live\":\"false\",\"notificationItems\":[" + String.join(",", items) + "]}";
    }

    private static String item(String psp, String reference, String success, String key) {
        String sig = Base64.getEncoder().encodeToString(
                Signatures.hmacSha256(key, psp + ":" + reference + ":AUTHORISATION:" + success));
        return """
                {"NotificationRequestItem":{"eventCode":"AUTHORISATION","success":"%s","pspReference":"%s",
                 "merchantReference":"%s","eventDate":"2026-10-29T12:00:00Z",
                 "additionalData":{"hmacSignature":"%s"}}}""".formatted(success, psp, reference, sig);
    }
}
