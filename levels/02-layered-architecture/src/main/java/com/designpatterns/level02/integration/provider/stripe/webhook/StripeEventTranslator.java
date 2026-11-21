package com.designpatterns.level02.integration.provider.stripe.webhook;

import com.designpatterns.level02.integration.webhook.ProviderEventTranslator;
import com.designpatterns.level02.integration.webhook.WebhookTranslation;
import com.designpatterns.level02.integration.webhook.WebhookTranslation.ReceivedEvent;
import com.designpatterns.level02.model.ProviderId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * FLOW C.3 for Stripe: verify, then map. Replaces Level 1's StripeWebhookController.
 *
 * <p>{@code @Component} + implementing {@code ProviderEventTranslator} is all it takes to register: Spring
 * puts it in the {@code List} given to {@code WebhookTranslators}. {@code @Value("${...}")} injects one
 * setting from application.yml (the shared secret used to check the signature).
 */
@Component
class StripeEventTranslator implements ProviderEventTranslator {

    private final StripeWebhookVerifier verifier;

    StripeEventTranslator(@Value("${payment.stripe.webhook-secret}") String secret, Clock clock) {
        this.verifier = new StripeWebhookVerifier(secret, clock);
    }

    @Override
    public ProviderId provider() {
        return StripeWebhookMapper.STRIPE;
    }

    @Override
    public WebhookTranslation translate(Map<String, String> headers, String body) {
        // HMAC signature check: proves the request comes from Stripe and the body wasn't changed.
        // Without it anybody could POST "payment succeeded" to our endpoint.
        if (!verifier.verify(headers.get("sim-signature"), body)) {
            return new WebhookTranslation.Rejected(); // forged or tampered: nothing is stored
        }
        return StripeWebhookMapper.toEvent(body)
                .<WebhookTranslation>map(event -> new WebhookTranslation.Events(List.of(new ReceivedEvent(event, body)), null))
                .orElseGet(WebhookTranslation.Unreadable::new);
    }
}
