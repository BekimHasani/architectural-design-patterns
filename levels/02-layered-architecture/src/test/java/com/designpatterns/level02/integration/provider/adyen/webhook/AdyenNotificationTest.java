package com.designpatterns.level02.integration.provider.adyen.webhook;

import com.designpatterns.level02.integration.support.Signatures;
import com.designpatterns.level02.model.ReportedOutcome;

import com.designpatterns.level02.integration.provider.adyen.webhook.AdyenNotificationMapper.Item;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdyenNotificationTest {

    private static final String KEY = "adyen_test";

    @Test
    void aBatchBecomesOneEventPerItem() {
        List<Item> items = AdyenNotificationMapper.parse(batch(item("psp1", "att_1", "true"), item("psp2", "att_2", "false")))
                .orElseThrow();

        assertThat(items).hasSize(2);
        assertThat(items).allMatch(item -> AdyenNotificationMapper.verify(item, KEY));
        assertThat(AdyenNotificationMapper.toEvent(items.get(0)).outcome()).isEqualTo(ReportedOutcome.AUTHORIZED);
        assertThat(AdyenNotificationMapper.toEvent(items.get(1)).outcome()).isEqualTo(ReportedOutcome.DECLINED);
        assertThat(AdyenNotificationMapper.toEvent(items.get(0)).attemptId()).isEqualTo("att_1");
    }

    @Test
    void noEventIdFromAdyenSoTheIdentityIsBuiltFromTheItem() {
        Item item = AdyenNotificationMapper.parse(batch(item("psp1", "att_1", "true"))).orElseThrow().get(0);

        assertThat(AdyenNotificationMapper.toEvent(item).eventId()).isEqualTo("psp1:AUTHORISATION:true");
    }

    @Test
    void eachItemHasItsOwnSignature() {
        String forged = batch(item("psp1", "att_1", "true")).replace("\"success\":\"true\"", "\"success\":\"false\"");
        Item item = AdyenNotificationMapper.parse(forged).orElseThrow().get(0);

        assertThat(AdyenNotificationMapper.verify(item, KEY)).isFalse();
    }

    private static String batch(String... items) {
        return "{\"live\":\"false\",\"notificationItems\":[" + String.join(",", items) + "]}";
    }

    private static String item(String psp, String reference, String success) {
        String sig = Base64.getEncoder().encodeToString(
                Signatures.hmacSha256(KEY, psp + ":" + reference + ":AUTHORISATION:" + success));
        return """
                {"NotificationRequestItem":{"eventCode":"AUTHORISATION","success":"%s","pspReference":"%s",
                 "merchantReference":"%s","eventDate":"2026-10-29T12:00:00Z",
                 "additionalData":{"hmacSignature":"%s"}}}""".formatted(success, psp, reference, sig);
    }
}
