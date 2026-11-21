package com.designpatterns.level08.payments.application.events;

import com.designpatterns.level08.payments.application.ports.ConcurrentUpdateException;
import com.designpatterns.level08.payments.application.ports.PaymentAttemptLog;
import com.designpatterns.level08.payments.application.ports.PaymentRepository;
import com.designpatterns.level08.payments.application.ports.ProviderEventInbox;
import com.designpatterns.level08.payments.domain.PaymentId;
import com.designpatterns.level08.payments.domain.Payment;
import com.designpatterns.level08.payments.domain.PaymentAttempt;
import com.designpatterns.level08.payments.domain.ReportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Use case: "a provider told us something about a payment". Called by all three webhook
 * endpoints with the same command.
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
public final class RecordProviderPaymentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(RecordProviderPaymentEventHandler.class);
    private static final int MAX_TRIES = 3;

    private final ProviderEventInbox inbox;
    private final PaymentAttemptLog attempts;
    private final PaymentRepository payments;

    public RecordProviderPaymentEventHandler(ProviderEventInbox inbox, PaymentAttemptLog attempts,
                                             PaymentRepository payments) {
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.payments = Objects.requireNonNull(payments, "payments");
    }

    public EventOutcome handle(RecordProviderPaymentEvent event, String rawPayload) {
        if (!inbox.receive(event.providerId(), event.eventId(), rawPayload)) {
            return EventOutcome.DUPLICATE;
        }
        EventOutcome outcome = event.outcome() == null ? EventOutcome.IGNORED : apply(event);
        inbox.markHandled(event.providerId(), event.eventId(), outcome.name());
        log.info("{} event {} ({}): {}", event.providerId(), event.eventId(), event.outcome(), outcome);
        return outcome;
    }

    private EventOutcome apply(RecordProviderPaymentEvent event) {
        Optional<PaymentId> paymentId = Optional.ofNullable(event.attemptId())
                .flatMap(attempts::find)
                .map(PaymentAttempt::paymentId);
        if (paymentId.isEmpty()) {
            return EventOutcome.UNMATCHED;
        }
        for (int attempt = 1; ; attempt++) {
            Payment payment = payments.find(paymentId.get()).orElseThrow().actingAs("provider:" + event.providerId());
            ReportResult result = payment.report(event.outcome(), event.providerRef());
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
