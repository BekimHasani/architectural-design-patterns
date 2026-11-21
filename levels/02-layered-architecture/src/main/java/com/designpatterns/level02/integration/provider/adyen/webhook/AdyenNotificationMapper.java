package com.designpatterns.level02.integration.provider.adyen.webhook;

import com.designpatterns.level02.integration.support.ProviderHttp;
import com.designpatterns.level02.integration.support.Signatures;
import com.designpatterns.level02.integration.webhook.ProviderEvent;
import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.model.ReportedOutcome;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adyen's BATCHED notifications → one event per item. Each item carries its own HMAC, and
 * Adyen sends no event id: the identity of a notification is {@code pspReference + eventCode + success}.
 */
final class AdyenNotificationMapper {

    static final ProviderId ADYEN = ProviderId.of("adyen");

    record Batch(List<Wrapper> notificationItems) {
    }

    record Wrapper(@JsonProperty("NotificationRequestItem") Item item) {
    }

    record Item(String eventCode, String success, String pspReference, String merchantReference,
                String eventDate, Map<String, String> additionalData) {

        String eventId() {
            return pspReference + ":" + eventCode + ":" + success;
        }

        String signedData() {
            return pspReference + ":" + merchantReference + ":" + eventCode + ":" + success;
        }
    }

    private AdyenNotificationMapper() {
    }

    static Optional<List<Item>> parse(String body) {
        return ProviderHttp.parse(body, Batch.class)
                .filter(b -> b.notificationItems() != null)
                .map(b -> b.notificationItems().stream().map(Wrapper::item).toList());
    }

    /** {@code additionalData.hmacSignature = base64 hmac_sha256(key, psp:merchantRef:eventCode:success)}. */
    static boolean verify(Item item, String hmacKey) {
        String signature = item.additionalData() == null ? null : item.additionalData().get("hmacSignature");
        if (signature == null) {
            return false;
        }
        try {
            return Signatures.matches(Signatures.hmacSha256(hmacKey, item.signedData()),
                    Base64.getDecoder().decode(signature));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    static ProviderEvent toEvent(Item item) {
        if (!"AUTHORISATION".equals(item.eventCode())) {
            return ProviderEvent.irrelevant(ADYEN, item.eventId());
        }
        ReportedOutcome outcome = "true".equals(item.success()) ? ReportedOutcome.AUTHORIZED : ReportedOutcome.DECLINED;
        return new ProviderEvent(ADYEN, item.eventId(), item.merchantReference(), item.pspReference(),
                outcome, item.eventDate() == null ? null : Instant.parse(item.eventDate()));
    }
}
