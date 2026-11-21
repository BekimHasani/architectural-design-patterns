package com.designpatterns.level01.providers.stripe.webhook;

import com.designpatterns.level01.payments.application.events.RecordProviderPaymentEvent;
import com.designpatterns.level01.payments.domain.ProviderId;
import com.designpatterns.level01.payments.domain.ReportedOutcome;
import com.designpatterns.level01.providers.support.ProviderHttp;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Stripe's event JSON → our one command. The DTOs are package-private and die here. */
final class StripeWebhookMapper {

    static final ProviderId STRIPE = ProviderId.of("stripe");

    record Event(String id, String type, long created, Data data) {
    }

    record Data(Intent object) {
    }

    record Intent(String id, String status, Map<String, String> metadata) {
    }

    private StripeWebhookMapper() {
    }

    /** Empty if the body isn't a Stripe event at all. */
    static Optional<RecordProviderPaymentEvent> toCommand(String body) {
        return ProviderHttp.parse(body, Event.class)
                .filter(e -> e.id() != null && e.type() != null)
                .map(StripeWebhookMapper::toCommand);
    }

    private static RecordProviderPaymentEvent toCommand(Event e) {
        ReportedOutcome outcome = switch (e.type()) {
            case "payment_intent.processing" -> ReportedOutcome.PROCESSING;
            case "payment_intent.amount_capturable_updated" -> ReportedOutcome.AUTHORIZED;
            case "payment_intent.succeeded" -> ReportedOutcome.CAPTURED;
            case "payment_intent.payment_failed" -> ReportedOutcome.DECLINED;
            default -> null; // charge.updated, customer.created, …: not about payment state
        };
        if (outcome == null || e.data() == null || e.data().object() == null) {
            return RecordProviderPaymentEvent.irrelevant(STRIPE, e.id());
        }
        Intent intent = e.data().object();
        String attemptId = intent.metadata() == null ? null : intent.metadata().get("attempt_id");
        return new RecordProviderPaymentEvent(STRIPE, e.id(), attemptId, intent.id(), outcome,
                Instant.ofEpochSecond(e.created()));
    }
}
