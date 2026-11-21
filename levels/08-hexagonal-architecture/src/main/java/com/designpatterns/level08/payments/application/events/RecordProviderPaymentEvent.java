package com.designpatterns.level08.payments.application.events;

import com.designpatterns.level08.payments.domain.ProviderId;
import com.designpatterns.level08.payments.domain.ReportedOutcome;

import java.time.Instant;
import java.util.Objects;

/**
 * THE ONE COMMAND every provider's webhook mapper produces. Three payload formats come in;
 * this single shape goes on. Nothing provider-specific survives the mapper.
 *
 * @param eventId   the provider's id for this notification: the deduplication key
 * @param attemptId OUR reference, echoed back by the provider: how we find the payment
 * @param outcome   what the provider says happened, or null if the event is not about payment state
 */
public record RecordProviderPaymentEvent(ProviderId providerId, String eventId, String attemptId, String providerRef,
                                         ReportedOutcome outcome, Instant occurredAt) {

    public RecordProviderPaymentEvent {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(eventId, "eventId");
    }

    /** An event we acknowledge but don't act on (e.g. PayPal's CHECKOUT.ORDER.APPROVED). */
    public static RecordProviderPaymentEvent irrelevant(ProviderId providerId, String eventId) {
        return new RecordProviderPaymentEvent(providerId, eventId, null, null, null, null);
    }
}
