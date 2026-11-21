package com.designpatterns.level02.persistence.jpa;

import com.designpatterns.level02.model.AttemptOutcome;
import com.designpatterns.level02.model.MerchantId;
import com.designpatterns.level02.model.Money;
import com.designpatterns.level02.model.Payment;
import com.designpatterns.level02.model.PaymentAttempt;
import com.designpatterns.level02.model.PaymentId;
import com.designpatterns.level02.model.PaymentOperation;
import com.designpatterns.level02.model.PaymentStatus;
import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.model.exceptions.ConcurrentUpdateException;
import com.designpatterns.level02.model.exceptions.PaymentRuleViolationException;
import com.designpatterns.level02.persistence.StoredPaymentEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The JPA adapters against a real (embedded H2) database, using schema.sql.
 *
 * <p>{@code @DataJpaTest} is a SLICE: only JPA, Spring Data and the classes imported below, not the
 * whole application. Its default "roll back every test" transaction is switched OFF, because the
 * adapters open their own short transactions and the race tests need real commits.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaPaymentRepository.class, JpaPaymentAttemptRepository.class, JpaProviderEventInboxRepository.class,
        JpaIdempotencyKeyRepository.class, JpaAuditEventRepository.class, JpaPersistenceTest.ClockConfig.class})
class JpaPersistenceTest {

    private static final ProviderId STRIPE = ProviderId.of("stripe");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    JpaPaymentRepository repository;
    @Autowired
    JpaPaymentAttemptRepository attempts;
    @Autowired
    JpaProviderEventInboxRepository inbox;
    @Autowired
    JpaIdempotencyKeyRepository idempotencyKeys;
    @Autowired
    JpaAuditEventRepository audit;
    @Autowired
    SpringDataOperations operationRows;

    @Test
    void paymentWithOperationsRoundTrips() {
        Payment payment = repository.save(newPayment());
        payment.startAttempt("att_" + UUID.randomUUID(), STRIPE);
        payment = repository.save(payment);
        payment.authorized("pi_1");
        payment.startCapture("op_c_" + UUID.randomUUID(), Money.of(600, "EUR"));
        payment.operationSucceeded(payment.operations().get(0).operationId(), "pi_1");
        payment.startRefund("op_r_" + UUID.randomUUID(), Money.of(200, "EUR"));
        repository.save(payment);

        Payment loaded = repository.find(payment.id()).orElseThrow();

        assertThat(loaded.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(loaded.version()).isEqualTo(3);
        assertThat(loaded.captured()).isEqualTo(Money.of(600, "EUR"));
        assertThat(loaded.refundable()).as("the in-flight refund reservation survives a reload").isEqualTo(Money.of(400, "EUR"));
        assertThat(loaded.operations()).extracting(PaymentOperation::status)
                .containsExactly(PaymentOperation.Status.SUCCEEDED, PaymentOperation.Status.IN_FLIGHT);
    }

    @Test
    void theVersionGoesUpOnEverySave_evenWhenOnlyAnOperationChanged() {
        Payment payment = newPayment();
        payment.startAttempt("att_" + UUID.randomUUID(), STRIPE);
        payment.charged("ref");
        payment = repository.save(payment);
        payment.startRefund("op_" + UUID.randomUUID(), Money.of(100, "EUR")); // the payments row itself doesn't change
        Payment saved = repository.save(payment);

        assertThat(repository.find(saved.id()).orElseThrow().version()).isEqualTo(saved.version()).isEqualTo(2);
    }

    @Test
    void eventsAreStoredWithTheStateAndReadBackInOrder() {
        Payment payment = repository.save(newPayment());
        payment.startAttempt("att_" + UUID.randomUUID(), STRIPE);
        payment.actingAs("provider:stripe");
        payment.authorized("pi_1");
        repository.save(payment);

        assertThat(audit.forPayment(payment.id())).extracting(StoredPaymentEvent::type)
                .containsExactly("PaymentCreated", "AttemptStarted", "PaymentAuthorized");
        assertThat(audit.forPayment(payment.id()).get(2).actor()).isEqualTo("provider:stripe");
    }

    @Test
    void aLostOptimisticRaceWritesNeitherStateNorEvents() {
        PaymentId id = repository.save(newPayment()).id();
        Payment a = repository.find(id).orElseThrow();
        Payment b = repository.find(id).orElseThrow();
        a.startAttempt("att_a_" + id, STRIPE);
        repository.save(a);
        b.startAttempt("att_b_" + id, ProviderId.of("adyen"));

        assertThatThrownBy(() -> repository.save(b)).isInstanceOf(ConcurrentUpdateException.class);

        assertThat(repository.find(id).orElseThrow().attemptId()).isEqualTo("att_a_" + id);
        assertThat(audit.forPayment(id)).extracting(StoredPaymentEvent::detail).doesNotContain("provider adyen");
    }

    @Test
    void savingANewPaymentTwiceIsARaceNotAnOverwrite() {
        Payment payment = newPayment();
        repository.save(payment);

        assertThatThrownBy(() -> repository.save(payment)).isInstanceOf(ConcurrentUpdateException.class);
    }

    @Test
    void findsPaymentsByStatusAndWithUnknownOperations() {
        Payment payment = newPayment();
        payment.startAttempt("att_" + UUID.randomUUID(), STRIPE);
        payment.charged("x");
        payment.startRefund("op_lost_" + UUID.randomUUID(), null);
        payment.operationUnknown(payment.operations().get(0).operationId());
        repository.save(payment);

        assertThat(repository.findWithUnknownOperations()).extracting(Payment::id).contains(payment.id());
        assertThat(repository.findByStatus(PaymentStatus.CAPTURED)).extracting(Payment::id).contains(payment.id());
    }

    @Test
    void attemptIsInsertedThenUpdatedAndFoundById() {
        PaymentAttempt attempt = PaymentAttempt.start(PaymentId.newId(), STRIPE, Clock.systemUTC().instant());

        attempts.save(attempt);
        attempts.save(attempt.finish(AttemptOutcome.OUTCOME_UNKNOWN, attempt.startedAt().plusSeconds(3)));

        assertThat(attempts.find(attempt.attemptId()))
                .hasValueSatisfying(a -> assertThat(a.outcome()).isEqualTo(AttemptOutcome.OUTCOME_UNKNOWN));
        assertThat(attempts.forPayment(attempt.paymentId())).hasSize(1);
    }

    @Test
    void inboxDeduplicates() {
        String event = "evt_" + UUID.randomUUID();

        assertThat(inbox.receive(STRIPE, event, "{}")).isTrue();
        assertThat(inbox.receive(STRIPE, event, "{}")).as("never finished: process again").isTrue();
        inbox.markHandled(STRIPE, event, "APPLIED");
        assertThat(inbox.receive(STRIPE, event, "{}")).isFalse();
    }

    @Test
    void idempotencyKeyLifecycle() {
        String key = "key-" + UUID.randomUUID();

        assertThat(idempotencyKeys.insertInProgress(key, "hash")).isTrue();
        assertThat(idempotencyKeys.insertInProgress(key, "hash")).isFalse();
        assertThat(idempotencyKeys.find(key)).hasValueSatisfying(k -> assertThat(k.completed()).isFalse());

        idempotencyKeys.complete(key, 200, "{\"ok\":true}");
        idempotencyKeys.release(key); // only releases keys still in progress

        assertThat(idempotencyKeys.find(key)).hasValueSatisfying(k -> {
            assertThat(k.completed()).isTrue();
            assertThat(k.httpStatus()).isEqualTo(200);
            assertThat(k.responseBody()).isEqualTo("{\"ok\":true}");
        });
    }

    @Test
    void tenConcurrentInsertsOneWinner() throws Exception {
        String key = "race-" + UUID.randomUUID();
        List<Callable<Boolean>> claims = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            claims.add(() -> idempotencyKeys.insertInProgress(key, "hash"));
        }
        List<Boolean> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (Future<Boolean> f : pool.invokeAll(claims)) {
                results.add(f.get());
            }
        }

        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
    }

    /** The whole money model rests on this: of N writers holding the same version, exactly one wins. */
    @Test
    void ofManyConcurrentWritersWithTheSameVersionExactlyOneWins() throws Exception {
        for (int round = 0; round < 50; round++) {
            Payment created = newPayment();
            created.startAttempt("att_race_" + UUID.randomUUID(), STRIPE);
            created.charged("ref");
            PaymentId id = repository.save(created).id();

            List<Callable<Boolean>> writers = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String operationId = "op_race_" + UUID.randomUUID();
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
        for (int round = 0; round < 50; round++) {
            Payment created = newPayment();
            created.startAttempt("att_race_" + UUID.randomUUID(), STRIPE);
            created.charged("ref");
            PaymentId id = repository.save(created).id();

            List<Callable<Void>> writers = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                writers.add(() -> {
                    for (int tries = 0; tries < 20; tries++) {
                        try {
                            Payment copy = repository.find(id).orElseThrow();
                            copy.startRefund("op_race_" + UUID.randomUUID(), Money.of(400, "EUR"));
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
            long reserved = operationRows.findByPaymentIdOrderByPosition(id.value()).stream()
                    .filter(op -> op.type().equals("REFUND"))
                    .mapToLong(PaymentOperationEntity::amountMinor)
                    .sum();
            assertThat(reserved).as("round " + round).isLessThanOrEqualTo(1000);
        }
    }

    private static Payment newPayment() {
        return Payment.create(PaymentId.newId(), new MerchantId("acme"), "c-1", Money.of(1000, "EUR"));
    }
}
