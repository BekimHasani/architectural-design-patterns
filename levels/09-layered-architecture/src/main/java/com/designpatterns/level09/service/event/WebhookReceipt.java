package com.designpatterns.level09.service.event;

/**
 * What the service made of a webhook request. The presentation layer turns it into an HTTP status;
 * the service layer itself knows nothing about HTTP.
 */
public sealed interface WebhookReceipt {

    /** No translator for this provider id. */
    record UnknownProvider() implements WebhookReceipt {
    }

    /** Signature missing, wrong or too old: nothing was stored. */
    record BadSignature() implements WebhookReceipt {
    }

    /** Signed, but not a payload we understand. */
    record Unreadable() implements WebhookReceipt {
    }

    /**
     * Stored and handled (or recognised as a duplicate).
     *
     * @param acknowledgement the exact body the provider wants back, or null for an empty 200
     */
    record Accepted(String acknowledgement) implements WebhookReceipt {
    }
}
