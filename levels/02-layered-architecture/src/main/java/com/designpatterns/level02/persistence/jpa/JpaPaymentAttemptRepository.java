package com.designpatterns.level02.persistence.jpa;

import com.designpatterns.level02.model.AttemptOutcome;
import com.designpatterns.level02.model.PaymentAttempt;
import com.designpatterns.level02.model.PaymentId;
import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.persistence.PaymentAttemptRepository;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
class JpaPaymentAttemptRepository implements PaymentAttemptRepository {

    private final SpringDataAttempts attempts;

    JpaPaymentAttemptRepository(SpringDataAttempts attempts) {
        this.attempts = attempts;
    }

    /** Spring Data's save() MERGES an entity with an assigned id: INSERT the first time, UPDATE after. */
    @Override
    public void save(PaymentAttempt a) {
        attempts.save(new PaymentAttemptEntity(a.attemptId(), a.paymentId().value(), a.providerId().value(),
                a.startedAt(), a.duration().toMillis(), a.outcome().name()));
    }

    @Override
    public List<PaymentAttempt> forPayment(PaymentId paymentId) {
        return attempts.findByPaymentIdOrderByStartedAtAscAttemptIdAsc(paymentId.value()).stream()
                .map(JpaPaymentAttemptRepository::toModel)
                .toList();
    }

    @Override
    public Optional<PaymentAttempt> find(String attemptId) {
        return attempts.findById(attemptId).map(JpaPaymentAttemptRepository::toModel);
    }

    private static PaymentAttempt toModel(PaymentAttemptEntity e) {
        return new PaymentAttempt(e.attemptId(), new PaymentId(e.paymentId()), ProviderId.of(e.providerId()),
                e.startedAt(), Duration.ofMillis(e.durationMs()), AttemptOutcome.valueOf(e.outcome()));
    }
}
