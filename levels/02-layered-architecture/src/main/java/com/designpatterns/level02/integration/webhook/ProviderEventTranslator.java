package com.designpatterns.level02.integration.webhook;

import com.designpatterns.level02.model.ProviderId;

import java.util.Map;

/**
 * STRATEGY, one per provider that sends webhooks: verify the signature and turn the provider's
 * payload into our {@link ProviderEvent}s.
 *
 * <p>LAYERED CHANGE: in Level 1 each provider folder had its own {@code @RestController}. Here only the
 * presentation layer has controllers, so there is ONE {@code POST /webhooks/{provider}} endpoint and the
 * provider-specific part shrinks to this plain, easily tested class. A new provider adds a translator;
 * the controller doesn't change (Open/Closed).
 */
public interface ProviderEventTranslator {

    ProviderId provider();

    /**
     * @param headers the request headers, with LOWER-CASE names
     * @param body    the raw body, exactly as received (signatures are over the raw bytes)
     */
    WebhookTranslation translate(Map<String, String> headers, String body);
}
