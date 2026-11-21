package com.designpatterns.level02.integration.provider.paypal.webhook;

import com.designpatterns.level02.integration.support.ProviderHttp;
import com.designpatterns.level02.integration.webhook.ProviderEvent;
import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.model.ReportedOutcome;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Optional;

/** PayPal's event JSON → our one ProviderEvent. */
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

    static Optional<ProviderEvent> toEvent(String body) {
        return ProviderHttp.parse(body, Event.class)
                .filter(e -> e.id() != null && e.eventType() != null)
                .map(PayPalWebhookMapper::toEvent);
    }

    private static ProviderEvent toEvent(Event e) {
        ReportedOutcome outcome = switch (e.eventType()) {
            case "PAYMENT.CAPTURE.COMPLETED" -> ReportedOutcome.CAPTURED;
            case "PAYMENT.CAPTURE.DENIED" -> ReportedOutcome.DECLINED;
            default -> null; // CHECKOUT.ORDER.APPROVED etc.: the customer flow already handles these
        };
        if (outcome == null || e.resource() == null) {
            return ProviderEvent.irrelevant(PAYPAL, e.id());
        }
        Resource r = e.resource();
        String orderId = r.supplementaryData() == null || r.supplementaryData().relatedIds() == null
                ? null : r.supplementaryData().relatedIds().orderId();
        return new ProviderEvent(PAYPAL, e.id(), r.customId(), orderId, outcome,
                e.createTime() == null ? null : Instant.parse(e.createTime()));
    }
}
