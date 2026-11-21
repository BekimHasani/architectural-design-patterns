package com.designpatterns.level08.payments.application.events;

import com.designpatterns.level08.payments.domain.MerchantId;
import com.designpatterns.level08.payments.domain.Money;
import com.designpatterns.level08.payments.domain.Payment;
import com.designpatterns.level08.payments.domain.PaymentAttempt;
import com.designpatterns.level08.payments.domain.PaymentId;
import com.designpatterns.level08.payments.domain.PaymentStatus;
import com.designpatterns.level08.payments.domain.ProviderId;
import com.designpatterns.level08.payments.domain.ReportedOutcome;
import com.designpatterns.level08.support.InMemoryPaymentAttemptLog;
import com.designpatterns.level08.support.InMemoryPaymentRepository;
import com.designpatterns.level08.support.InMemoryProviderEventInbox;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RecordProviderPaymentEventHandlerTest {

    private static final ProviderId STRIPE = ProviderId.of("stripe");

    private final InMemoryProviderEventInbox inbox = new InMemoryProviderEventInbox();
    private final InMemoryPaymentAttemptLog attempts = new InMemoryPaymentAttemptLog();
    private final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
    private final RecordProviderPaymentEventHandler handler = new RecordProviderPaymentEventHandler(inbox, attempts, payments);

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

    private static RecordProviderPaymentEvent event(String eventId, String attemptId, ReportedOutcome outcome) {
        return new RecordProviderPaymentEvent(STRIPE, eventId, attemptId, "pi_1", outcome, Instant.now());
    }
}
