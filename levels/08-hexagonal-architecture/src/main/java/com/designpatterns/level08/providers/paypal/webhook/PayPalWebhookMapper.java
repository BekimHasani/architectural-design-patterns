package com.designpatterns.level08.providers.paypal.webhook;

import com.designpatterns.level08.payments.application.events.RecordProviderPaymentEvent;
import com.designpatterns.level08.payments.domain.ProviderId;
import com.designpatterns.level08.payments.domain.ReportedOutcome;
import com.designpatterns.level08.providers.support.ProviderHttp;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Optional;

/** PayPal's event JSON → our one command. */
final class PayPalWebhookMapper {

    static final ProviderId PAYPAL = ProviderId.of("paypal");

    record Event(String id, @JsonProperty("event_type") String eventType,
                 @JsonProperty("create_time") String createTime, Resource resource) {
    }

    /** {@code custom_id} carries OUR reference back; {@code related_ids.order_id} is PayPal's order. */
    record Resource(String id, String status, @JsonProperty("custom_id") String customId,
                    @JsonProperty("supplementary_data") SupplementaryData supplementaryData) {
    }

    record SupplementaryData(@JsonProperty("related_ids") RelatedIds relatedIds) {
    }

    record RelatedIds(@JsonProperty("order_id") String orderId) {
    }

    private PayPalWebhookMapper() {
    }

    static Optional<RecordProviderPaymentEvent> toCommand(String body) {
        return ProviderHttp.parse(body, Event.class)
                .filter(e -> e.id() != null && e.eventType() != null)
                .map(PayPalWebhookMapper::toCommand);
    }

    private static RecordProviderPaymentEvent toCommand(Event e) {
        ReportedOutcome outcome = switch (e.eventType()) {
            case "PAYMENT.CAPTURE.COMPLETED" -> ReportedOutcome.CAPTURED;
            case "PAYMENT.CAPTURE.DENIED" -> ReportedOutcome.DECLINED;
            default -> null; // CHECKOUT.ORDER.APPROVED etc.: the customer flow already handles these
        };
        if (outcome == null || e.resource() == null) {
            return RecordProviderPaymentEvent.irrelevant(PAYPAL, e.id());
        }
        Resource r = e.resource();
        String orderId = r.supplementaryData() == null || r.supplementaryData().relatedIds() == null
                ? null : r.supplementaryData().relatedIds().orderId();
        return new RecordProviderPaymentEvent(PAYPAL, e.id(), r.customId(), orderId, outcome,
                e.createTime() == null ? null : Instant.parse(e.createTime()));
    }
}
