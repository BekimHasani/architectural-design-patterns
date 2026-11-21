package com.designpatterns.level02.integration.webhook;

import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.model.ReportedOutcome;


import java.time.Instant;
import java.util.Objects;

/**
 * THE ONE EVENT SHAPE every provider's webhook translator produces (was RecordProviderPaymentEvent).
 * Three payload formats come in; this single shape goes on. Nothing provider-specific survives the
 * mapper. It belongs to the integration layer because integration creates it; the service layer
 * consumes it.
 *
 * @param eventId   the provider's id for this notification: the deduplication key
 * @param attemptId OUR reference, echoed back by the provider: how we find the payment
 * @param outcome   what the provider says happened, or null if the event is not about payment state
 */
public record ProviderEvent(ProviderId providerId, String eventId, String attemptId, String providerRef,
                                         ReportedOutcome outcome, Instant occurredAt) {

    public ProviderEvent {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(eventId, "eventId");
    }

    /** An event we acknowledge but don't act on (e.g. PayPal's CHECKOUT.ORDER.APPROVED). */
    public static ProviderEvent irrelevant(ProviderId providerId, String eventId) {
        return new ProviderEvent(providerId, eventId, null, null, null, null);
    }
}
