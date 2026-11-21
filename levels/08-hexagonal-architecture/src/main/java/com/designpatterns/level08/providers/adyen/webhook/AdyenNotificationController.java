package com.designpatterns.level08.providers.adyen.webhook;

import com.designpatterns.level08.payments.application.events.RecordProviderPaymentEventHandler;
import com.designpatterns.level08.providers.adyen.webhook.AdyenNotificationMapper.Item;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * INBOUND ADAPTER: Adyen → us. One request may hold MANY notifications. Every item is
 * verified first (one bad signature rejects the whole batch: nothing stored), then each item
 * goes through the same command as Stripe's and PayPal's events. Adyen requires the literal
 * answer {@code [accepted]}, or it keeps retrying.
 */
@RestController
class AdyenNotificationController {

    private final RecordProviderPaymentEventHandler handler;
    private final String hmacKey;
    private final ObjectMapper json;

    AdyenNotificationController(RecordProviderPaymentEventHandler handler,
                                @Value("${payment.adyen.hmac-key}") String hmacKey, ObjectMapper json) {
        this.handler = handler;
        this.hmacKey = hmacKey;
        this.json = json;
    }

    @PostMapping("/webhooks/adyen")
    ResponseEntity<String> receive(@RequestBody String body) throws JsonProcessingException {
        Optional<List<Item>> items = AdyenNotificationMapper.parse(body);
        if (items.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!items.get().stream().allMatch(item -> AdyenNotificationMapper.verify(item, hmacKey))) {
            return ResponseEntity.status(401).build();
        }
        for (Item item : items.get()) {
            handler.handle(AdyenNotificationMapper.toCommand(item), json.writeValueAsString(item));
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("[accepted]");
    }
}
