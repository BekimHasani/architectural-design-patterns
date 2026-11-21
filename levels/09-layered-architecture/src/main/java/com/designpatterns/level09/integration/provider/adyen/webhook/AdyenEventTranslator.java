package com.designpatterns.level09.integration.provider.adyen.webhook;

import com.designpatterns.level09.integration.provider.adyen.webhook.AdyenNotificationMapper.Item;
import com.designpatterns.level09.integration.webhook.ProviderEventTranslator;
import com.designpatterns.level09.integration.webhook.WebhookTranslation;
import com.designpatterns.level09.integration.webhook.WebhookTranslation.ReceivedEvent;
import com.designpatterns.level09.model.ProviderId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adyen → us. One request may hold MANY notifications. Every item is verified first (one bad
 * signature rejects the whole batch: nothing stored). Adyen requires the literal answer
 * {@code [accepted]}, or it keeps retrying.
 */
@Component
class AdyenEventTranslator implements ProviderEventTranslator {

    static final String ACCEPTED = "[accepted]";

    private final String hmacKey;
    private final ObjectMapper json;

    AdyenEventTranslator(@Value("${payment.adyen.hmac-key}") String hmacKey, ObjectMapper json) {
        this.hmacKey = hmacKey;
        this.json = json;
    }

    @Override
    public ProviderId provider() {
        return AdyenNotificationMapper.ADYEN;
    }

    @Override
    public WebhookTranslation translate(Map<String, String> headers, String body) {
        Optional<List<Item>> items = AdyenNotificationMapper.parse(body);
        if (items.isEmpty()) {
            return new WebhookTranslation.Unreadable();
        }
        if (!items.get().stream().allMatch(item -> AdyenNotificationMapper.verify(item, hmacKey))) {
            return new WebhookTranslation.Rejected();
        }
        List<ReceivedEvent> events = items.get().stream()
                .map(item -> new ReceivedEvent(AdyenNotificationMapper.toEvent(item), raw(item)))
                .toList();
        return new WebhookTranslation.Events(events, ACCEPTED);
    }

    private String raw(Item item) {
        try {
            return json.writeValueAsString(item);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
