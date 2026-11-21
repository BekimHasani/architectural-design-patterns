package com.designpatterns.level01.platform.persistence;

import com.designpatterns.level01.audit.AuditEntry;
import com.designpatterns.level01.audit.AuditTrail;
import com.designpatterns.level01.payments.application.ports.ConcurrentUpdateException;
import com.designpatterns.level01.payments.application.ports.IdempotencyStore.Claim;
import com.designpatterns.level01.payments.domain.AttemptOutcome;
import com.designpatterns.level01.payments.domain.MerchantId;
import com.designpatterns.level01.payments.domain.Money;
import com.designpatterns.level01.payments.domain.Payment;
import com.designpatterns.level01.payments.domain.PaymentAttempt;
import com.designpatterns.level01.payments.domain.PaymentId;
import com.designpatterns.level01.payments.domain.PaymentOperation;
import com.designpatterns.level01.payments.domain.PaymentRuleViolationException;
import com.designpatterns.level01.payments.domain.PaymentStatus;
import com.designpatterns.level01.payments.domain.ProviderId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The JDBC adapters against a real (embedded H2) database, using schema.sql. */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JdbcPersistenceTest {

    private static final ProviderId STRIPE = ProviderId.of("stripe");

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager transactions;

    private JdbcPaymentRepository payments() {
        return new JdbcPaymentRepository(jdbc, new TransactionTemplate(transactions), Clock.systemUTC());
    }

    @Test
    void paymentWithOperationsRoundTrips() {
        JdbcPaymentRepository repository = payments();
        Payment payment = repository.save(newPayment());
        payment.startAttempt("att_1", STRIPE);
        payment = repository.save(payment);
        payment.authorized("pi_1");
        payment.startCapture("op_c", Money.of(600, "EUR"));
        payment.operationSucceeded("op_c", "pi_1");
        payment.startRefund("op_r", Money.of(200, "EUR"));
        repository.save(payment);

        Payment loaded = repository.find(payment.id()).orElseThrow();

        assertThat(loaded.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(loaded.captured()).isEqualTo(Money.of(600, "EUR"));
        assertThat(loaded.refundable()).as("the in-flight refund reservation survives a reload").isEqualTo(Money.of(400, "EUR"));
        assertThat(loaded.operations()).extracting(PaymentOperation::status)
                .containsExactly(PaymentOperation.Status.SUCCEEDED, PaymentOperation.Status.IN_FLIGHT);
    }

    @Test
    void eventsAreStoredWithTheStateAndReadBackInOrder() {
        JdbcPaymentRepository repository = payments();
        Payment payment = repository.save(newPayment());
        payment.startAttempt("att_1", STRIPE);
        payment.actingAs("provider:stripe");
        payment.authorized("pi_1");
        repository.save(payment);

        assertThat(new AuditTrail(jdbc).forPayment(payment.id())).extracting(AuditEntry::type)
                .containsExactly("PaymentCreated", "AttemptStarted", "PaymentAuthorized");
    }

    @Test
    void aLostOptimisticRaceWritesNeitherStateNorEvents() {
        JdbcPaymentRepository repository = payments();
        PaymentId id = repository.save(newPayment()).id();
        Payment a = repository.find(id).orElseThrow();
        Payment b = repository.find(id).orElseThrow();
        a.startAttempt("att_a", STRIPE);
        repository.save(a);
        b.startAttempt("att_b", ProviderId.of("adyen"));

        assertThatThrownBy(() -> repository.save(b)).isInstanceOf(ConcurrentUpdateException.class);

        assertThat(repository.find(id).orElseThrow().attemptId()).isEqualTo("att_a");
        assertThat(new AuditTrail(jdbc).forPayment(id)).extracting(AuditEntry::detail).doesNotContain("provider adyen");
    }

    @Test
    void findsPaymentsWithUnknownOperations() {
        JdbcPaymentRepository repository = payments();
        Payment payment = newPayment();
        payment.startAttempt("att_1", STRIPE);
        payment.charged("x");
        payment.startRefund("op_lost", null);
        payment.operationUnknown("op_lost");
        repository.save(payment);

        assertThat(repository.findWithUnknownOperations()).extracting(Payment::id).contains(payment.id());
    }

    @Test
    void attemptIsInsertedThenUpdatedAndFoundById() {
        JdbcPaymentAttemptLog log = new JdbcPaymentAttemptLog(jdbc);
        PaymentAttempt attempt = PaymentAttempt.start(PaymentId.newId(), STRIPE, Clock.systemUTC().instant());

        log.save(attempt);
        log.save(attempt.finish(AttemptOutcome.OUTCOME_UNKNOWN, attempt.startedAt().plusSeconds(3)));

        assertThat(log.find(attempt.attemptId())).hasValueSatisfying(a -> assertThat(a.outcome()).isEqualTo(AttemptOutcome.OUTCOME_UNKNOWN));
    }

    @Test
    void inboxDeduplicates() {
        JdbcProviderEventInbox inbox = new JdbcProviderEventInbox(jdbc, Clock.systemUTC());
        String event = "evt_" + System.nanoTime();

        assertThat(inbox.receive(STRIPE, event, "{}")).isTrue();
        assertThat(inbox.receive(STRIPE, event, "{}")).as("never finished: process again").isTrue();
        inbox.markHandled(STRIPE, event, "APPLIED");
        assertThat(inbox.receive(STRIPE, event, "{}")).isFalse();
    }

    @Test
    void tenConcurrentClaimsOneWinner() throws Exception {
        JdbcIdempotencyStore store = new JdbcIdempotencyStore(jdbc, Clock.systemUTC());
        String key = "race-" + System.nanoTime();
        List<Callable<Claim>> claims = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            claims.add(() -> store.claim(key, "hash"));
        }
        List<Claim> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (Future<Claim> f : pool.invokeAll(claims)) {
                results.add(f.get());
            }
        }

        assertThat(results).filteredOn(c -> c instanceof Claim.Claimed).hasSize(1);
    }

    /** The whole money model rests on this: of N writers holding the same version, exactly one wins. */
    @Test
    void ofManyConcurrentWritersWithTheSameVersionExactlyOneWins() throws Exception {
        JdbcPaymentRepository repository = payments();
        for (int round = 0; round < 100; round++) {
            Payment created = newPayment();
            created.startAttempt("att_race_" + java.util.UUID.randomUUID(), STRIPE);
            created.charged("ref");
            PaymentId id = repository.save(created).id();

            List<Callable<Boolean>> writers = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String operationId = "op_race_" + java.util.UUID.randomUUID();
                Payment copy = repository.find(id).orElseThrow();
                writers.add(() -> {
                    copy.startRefund(operationId, Money.of(400, "EUR"));
                    try {
                        repository.save(copy);
                        return true;
                    } catch (ConcurrentUpdateException lost) {
                        return false;
                    }
                });
            }
            int winners = 0;
            try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
                for (Future<Boolean> f : pool.invokeAll(writers)) {
                    winners += f.get() ? 1 : 0;
                }
            }
            assertThat(winners).as("round " + round).isEqualTo(1);
        }
    }

    /** Loading WHILE others commit must never give a current version with stale operations. */
    @Test
    void reservationsCanNeverExceedTheCapture_evenWhenLoadsOverlapCommits() throws Exception {
        JdbcPaymentRepository repository = payments();
        for (int round = 0; round < 150; round++) {
            Payment created = newPayment();
            created.startAttempt("att_race_" + java.util.UUID.randomUUID(), STRIPE);
            created.charged("ref");
            PaymentId id = repository.save(created).id();

            List<Callable<Void>> writers = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                writers.add(() -> {
                    for (int tries = 0; tries < 20; tries++) {
                        try {
                            Payment copy = repository.find(id).orElseThrow();
                            copy.startRefund("op_race_" + java.util.UUID.randomUUID(), Money.of(400, "EUR"));
                            repository.save(copy);
                        } catch (ConcurrentUpdateException | PaymentRuleViolationException expected) {
                            // lost the race, or nothing left to refund
                        }
                    }
                    return null;
                });
            }
            try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
                for (Future<Void> f : pool.invokeAll(writers)) {
                    f.get();
                }
            }
            long reserved = jdbc.sql("SELECT COALESCE(SUM(amount_minor), 0) FROM payment_operations WHERE payment_id = ? AND type = 'REFUND'")
                    .param(id.value()).query(Long.class).single();
            assertThat(reserved).as("round " + round).isLessThanOrEqualTo(1000);
        }
    }

    private static Payment newPayment() {
        return Payment.create(PaymentId.newId(), new MerchantId("acme"), "c-1", Money.of(1000, "EUR"));
    }
}
