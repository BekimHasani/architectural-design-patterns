package com.designpatterns.level02.integration.provider.paypal.webhook;

import com.designpatterns.level02.integration.webhook.ProviderEventTranslator;
import com.designpatterns.level02.integration.webhook.WebhookTranslation;
import com.designpatterns.level02.integration.webhook.WebhookTranslation.ReceivedEvent;
import com.designpatterns.level02.model.ProviderId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/** PayPal → us. Same shape as Stripe's, different format and signature headers. */
@Component
class PayPalEventTranslator implements ProviderEventTranslator {

    private final PayPalWebhookVerifier verifier;

    PayPalEventTranslator(@Value("${payment.paypal.webhook-secret}") String secret, Clock clock) {
        this.verifier = new PayPalWebhookVerifier(secret, clock);
    }

    @Override
    public ProviderId provider() {
        return PayPalWebhookMapper.PAYPAL;
    }

    @Override
    public WebhookTranslation translate(Map<String, String> headers, String body) {
        if (!verifier.verify(headers.get("sim-transmission-id"), headers.get("sim-transmission-time"),
                headers.get("sim-transmission-sig"), body)) {
            return new WebhookTranslation.Rejected();
        }
        return PayPalWebhookMapper.toEvent(body)
                .<WebhookTranslation>map(event -> new WebhookTranslation.Events(List.of(new ReceivedEvent(event, body)), null))
                .orElseGet(WebhookTranslation.Unreadable::new);
    }
}
