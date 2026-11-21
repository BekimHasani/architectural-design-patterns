package com.designpatterns.level09.service.event;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.designpatterns.level09.integration.webhook.ProviderEvent;
import com.designpatterns.level09.integration.webhook.ProviderEventTranslator;
import com.designpatterns.level09.integration.webhook.WebhookTranslation;
import com.designpatterns.level09.integration.webhook.WebhookTranslators;
import com.designpatterns.level09.model.Payment;
import com.designpatterns.level09.model.PaymentAttempt;
import com.designpatterns.level09.model.PaymentId;
import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.model.ReportResult;
import com.designpatterns.level09.model.exceptions.ConcurrentUpdateException;
import com.designpatterns.level09.persistence.PaymentAttemptRepository;
import com.designpatterns.level09.persistence.PaymentRepository;
import com.designpatterns.level09.persistence.ProviderEventInboxRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Use case: "a provider told us something about a payment". Every provider's
 * webhooks arrive through {@link #receive}, whatever the provider.
 *
 * <ol>
 *   <li>inbox: seen and handled before? → DUPLICATE, nothing else happens;</li>
 *   <li>find the payment through OUR attempt id. Because of write-ahead (Level 5), the attempt
 *       exists even when the webhook beats the provider's own API answer;</li>
 *   <li>{@link Payment#report}: the state machine decides; old news is ignored, contradictions surfaced;</li>
 *   <li>save with optimistic locking; if the checkout flow saved first, reload and report again.</li>
 * </ol>
 * Only database work, no outbound calls, so it is quick enough to run inside the webhook request.
 */
@Service
public class ProviderEventService {

    private static final Logger log = LoggerFactory.getLogger(ProviderEventService.class);
    private static final int MAX_TRIES = 3;

    private final ProviderEventInboxRepository inbox;
    private final PaymentAttemptRepository attempts;
    private final PaymentRepository payments;

    private final WebhookTranslators translators;

    public ProviderEventService(ProviderEventInboxRepository inbox, PaymentAttemptRepository attempts,
                                PaymentRepository payments, WebhookTranslators translators) {
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.translators = Objects.requireNonNull(translators, "translators");
    }

    /**
     * What each Level 8 webhook controller did, once for every provider: find the provider's translator,
     * let it verify and translate, then {@link #handle} each event.
     *
     * @param headers request headers with LOWER-CASE names
     */
    public WebhookReceipt receive(ProviderId providerId, Map<String, String> headers, String body) {
        // FLOW C.2: REGISTRY lookup of the provider's STRATEGY
        Optional<ProviderEventTranslator> translator = translators.forProvider(providerId);
        if (translator.isEmpty()) {
            return new WebhookReceipt.UnknownProvider();
        }
        // FLOW C.3: the translator checks the signature FIRST, then maps the body to our ProviderEvents
        return switch (translator.get().translate(headers, body)) {
            case WebhookTranslation.Rejected rejected -> new WebhookReceipt.BadSignature(); // nothing is stored
            case WebhookTranslation.Unreadable unreadable -> new WebhookReceipt.Unreadable();
            case WebhookTranslation.Events events -> {
                events.events().forEach(received -> handle(received.event(), received.rawPayload()));
                yield new WebhookReceipt.Accepted(events.acknowledgement()); // also for duplicates: "got it, stop retrying"
            }
        };
    }

    public EventOutcome handle(ProviderEvent event, String rawPayload) {
        // FLOW C.4: INBOX / IDEMPOTENT CONSUMER. Providers deliver "at least once": the same event may
        // come twice. The inbox's primary key (provider, event id) lets each event through only once.
        if (!inbox.receive(event.providerId(), event.eventId(), rawPayload)) {
            return EventOutcome.DUPLICATE;
        }
        EventOutcome outcome = event.outcome() == null ? EventOutcome.IGNORED : apply(event);
        inbox.markHandled(event.providerId(), event.eventId(), outcome.name()); // FLOW C.7: done, never again
        log.info("{} event {} ({}): {}", event.providerId(), event.eventId(), event.outcome(), outcome);
        return outcome;
    }

    private EventOutcome apply(ProviderEvent event) {
        // FLOW C.5: the provider echoes OUR attempt id; the attempt (saved by write-ahead) knows the payment
        Optional<PaymentId> paymentId = Optional.ofNullable(event.attemptId())
                .flatMap(attempts::find)
                .map(PaymentAttempt::paymentId);
        if (paymentId.isEmpty()) {
            return EventOutcome.UNMATCHED;
        }
        for (int attempt = 1; ; attempt++) {
            Payment payment = payments.find(paymentId.get()).orElseThrow().actingAs("provider:" + event.providerId());
            ReportResult result = payment.report(event.outcome(), event.providerRef()); // FLOW C.6: model decides
            if (result != ReportResult.APPLIED) {
                return switch (result) {
                    case ALREADY_KNOWN -> EventOutcome.ALREADY_KNOWN;
                    case STALE -> EventOutcome.STALE_IGNORED;
                    case CONFLICT -> {
                        log.error("CONFLICT: {} says {} but payment {} is {}. Needs review.",
                                event.providerId(), event.outcome(), payment.id(), payment.status());
                        yield EventOutcome.CONFLICT;
                    }
                    case APPLIED -> throw new IllegalStateException();
                };
            }
            try {
                payments.save(payment);
                return EventOutcome.APPLIED;
            } catch (ConcurrentUpdateException raced) {
                if (attempt == MAX_TRIES) {
                    throw raced; // the provider will retry the webhook; the inbox lets it through again
                }
            }
        }
    }
}
