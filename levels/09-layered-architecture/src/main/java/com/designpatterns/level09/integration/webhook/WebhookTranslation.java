package com.designpatterns.level09.integration.webhook;

import java.util.List;

/** What a translator made of one webhook request. A sealed type, so callers must handle every case. */
public sealed interface WebhookTranslation {

    /** Signature missing, wrong or too old. NOTHING may be stored. */
    record Rejected() implements WebhookTranslation {
    }

    /** Signed correctly but not a payload we understand. */
    record Unreadable() implements WebhookTranslation {
    }

    /**
     * Verified events (Adyen sends several per request).
     *
     * @param acknowledgement the exact body the provider expects back (Adyen: {@code [accepted]}), or null
     */
    record Events(List<ReceivedEvent> events, String acknowledgement) implements WebhookTranslation {

        public Events {
            events = List.copyOf(events);
        }
    }

    /** One event plus the raw text to keep in the inbox for audit. */
    record ReceivedEvent(ProviderEvent event, String rawPayload) {
    }
}
