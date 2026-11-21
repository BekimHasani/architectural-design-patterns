package com.designpatterns.level02.service.event;

import com.designpatterns.level02.integration.webhook.WebhookTranslators;
import java.util.List;
import java.util.Map;
import com.designpatterns.level02.integration.webhook.ProviderEventTranslator;
import com.designpatterns.level02.integration.webhook.WebhookTranslation;

import com.designpatterns.level02.integration.webhook.ProviderEvent;
import com.designpatterns.level02.model.MerchantId;
import com.designpatterns.level02.model.Money;
import com.designpatterns.level02.model.Payment;
import com.designpatterns.level02.model.PaymentAttempt;
import com.designpatterns.level02.model.PaymentId;
import com.designpatterns.level02.model.PaymentStatus;
import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.model.ReportedOutcome;
import com.designpatterns.level02.support.InMemoryPaymentAttemptRepository;
import com.designpatterns.level02.support.InMemoryPaymentRepository;
import com.designpatterns.level02.support.InMemoryProviderEventInboxRepository;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderEventServiceTest {

    private static final ProviderId STRIPE = ProviderId.of("stripe");

    private final InMemoryProviderEventInboxRepository inbox = new InMemoryProviderEventInboxRepository();
    private final InMemoryPaymentAttemptRepository attempts = new InMemoryPaymentAttemptRepository();
    private final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
    private final ScriptedTranslator stripeTranslator = new ScriptedTranslator();
    private final ProviderEventService handler = new ProviderEventService(inbox, attempts, payments,
            new WebhookTranslators(List.of(stripeTranslator)));

    /** A STUB translator: returns whatever the test scripted, so the service can be tested without signatures. */
    private static final class ScriptedTranslator implements ProviderEventTranslator {
        WebhookTranslation next;

        @Override
        public ProviderId provider() {
            return STRIPE;
        }

        @Override
        public WebhookTranslation translate(Map<String, String> headers, String body) {
            return next;
        }
    }

    @Test
    void receive_aWebhookForAProviderWithoutATranslatorIsUnknown() {
        assertThat(handler.receive(ProviderId.of("nobody"), Map.of(), "{}")).isEqualTo(new WebhookReceipt.UnknownProvider());
    }

    @Test
    void receive_aBadSignatureStoresNothing() {
        stripeTranslator.next = new WebhookTranslation.Rejected();

        assertThat(handler.receive(STRIPE, Map.of(), "{}")).isEqualTo(new WebhookReceipt.BadSignature());
        assertThat(inbox.events).isEmpty();
    }

    @Test
    void receive_unreadableIsReportedAsSuch() {
        stripeTranslator.next = new WebhookTranslation.Unreadable();

        assertThat(handler.receive(STRIPE, Map.of(), "garbage")).isEqualTo(new WebhookReceipt.Unreadable());
    }

    @Test
    void receive_handlesEveryEventOfABatch_andPassesTheAcknowledgementOn() {
        String first = pendingPayment();
        String second = pendingPayment();
        stripeTranslator.next = new WebhookTranslation.Events(List.of(
                new WebhookTranslation.ReceivedEvent(event("evt_a", first, ReportedOutcome.AUTHORIZED), "{a}"),
                new WebhookTranslation.ReceivedEvent(event("evt_b", second, ReportedOutcome.DECLINED), "{b}")), "[accepted]");

        assertThat(handler.receive(STRIPE, Map.of(), "batch")).isEqualTo(new WebhookReceipt.Accepted("[accepted]"));
        assertThat(payments.findByStatus(PaymentStatus.AUTHORIZED)).hasSize(1);
        assertThat(payments.findByStatus(PaymentStatus.DECLINED)).hasSize(1);
        assertThat(inbox.events).containsValues("APPLIED");
    }

    @Test
    void appliesThroughOurAttemptId_recordingWhoSaidIt() {
        String attemptId = pendingPayment();

        assertThat(handler.handle(event("evt_1", attemptId, ReportedOutcome.AUTHORIZED), "{}")).isEqualTo(EventOutcome.APPLIED);

        assertThat(payments.findByStatus(PaymentStatus.AUTHORIZED)).hasSize(1);
        assertThat(payments.events.get(payments.events.size() - 1).actor()).isEqualTo("provider:stripe");
    }

    @Test
    void duplicatesAndStaleNewsChangeNothing() {
        String attemptId = pendingPayment();
        handler.handle(event("evt_2", attemptId, ReportedOutcome.AUTHORIZED), "{}");

        assertThat(handler.handle(event("evt_2", attemptId, ReportedOutcome.AUTHORIZED), "{}")).isEqualTo(EventOutcome.DUPLICATE);
        assertThat(handler.handle(event("evt_1", attemptId, ReportedOutcome.PROCESSING), "{}")).isEqualTo(EventOutcome.STALE_IGNORED);
        assertThat(payments.findByStatus(PaymentStatus.AUTHORIZED)).hasSize(1);
    }

    @Test
    void anEventForAnAttemptWeDontKnowIsUnmatched() {
        assertThat(handler.handle(event("evt_9", "att_not-ours", ReportedOutcome.AUTHORIZED), "{}")).isEqualTo(EventOutcome.UNMATCHED);
    }

    private String pendingPayment() {
        Payment payment = Payment.create(PaymentId.newId(), new MerchantId("acme"), "c-1", Money.of(1000, "EUR"));
        PaymentAttempt attempt = PaymentAttempt.start(payment.id(), STRIPE, Instant.now());
        payment.startAttempt(attempt.attemptId(), STRIPE);
        payments.save(payment);
        attempts.save(attempt);
        return attempt.attemptId();
    }

    private static ProviderEvent event(String eventId, String attemptId, ReportedOutcome outcome) {
        return new ProviderEvent(STRIPE, eventId, attemptId, "pi_1", outcome, Instant.now());
    }
}
