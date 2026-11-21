package com.designpatterns.level02.presentation.webhook;

import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.service.event.ProviderEventService;
import com.designpatterns.level02.service.event.WebhookReceipt;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ONE webhook endpoint for every provider. Level 1 had a controller per provider inside each provider
 * folder. In a layered design a controller is presentation and may only call services, so the
 * provider-specific verify + map moved down into {@code integration} as translators. The URLs stay the
 * same: {@code /webhooks/stripe}, {@code /webhooks/paypal}, {@code /webhooks/adyen}.
 */
@RestController
class ProviderWebhookController {

    private final ProviderEventService events;

    ProviderWebhookController(ProviderEventService events) {
        this.events = events;
    }

    /**
     * FLOW C.1: a provider calls us. {@code @RequestHeader Map} = all headers; {@code @RequestBody String} =
     * the raw body, untouched, because the signature was computed over those exact bytes.
     */
    @PostMapping("/webhooks/{provider}")
    ResponseEntity<String> receive(@PathVariable String provider, @RequestHeader Map<String, String> headers,
                                   @RequestBody String body) {
        // FLOW C.2 – C.7 in the service; FLOW C.8: the receipt (a sealed type) → HTTP status
        return switch (events.receive(ProviderId.of(provider), lowerCase(headers), body)) {
            case WebhookReceipt.UnknownProvider unknown -> ResponseEntity.notFound().build();
            case WebhookReceipt.BadSignature forged -> ResponseEntity.status(401).build(); // nothing stored
            case WebhookReceipt.Unreadable garbage -> ResponseEntity.badRequest().build();
            case WebhookReceipt.Accepted accepted -> accepted.acknowledgement() == null
                    ? ResponseEntity.ok().build() // also for duplicates: "got it, stop retrying"
                    : ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(accepted.acknowledgement());
        };
    }

    /** HTTP header names are case-insensitive; translators can rely on lower case. */
    private static Map<String, String> lowerCase(Map<String, String> headers) {
        Map<String, String> result = new HashMap<>();
        headers.forEach((name, value) -> result.put(name.toLowerCase(Locale.ROOT), value));
        return result;
    }
}
