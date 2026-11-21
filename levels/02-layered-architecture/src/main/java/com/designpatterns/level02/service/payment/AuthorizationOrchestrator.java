package com.designpatterns.level02.service.payment;

import org.springframework.stereotype.Service;

import com.designpatterns.level02.integration.gateway.AuthorizeRequest;
import com.designpatterns.level02.integration.gateway.GatewayResult;
import com.designpatterns.level02.integration.gateway.ProviderCredentials;
import com.designpatterns.level02.integration.gateway.ProviderGateway;
import com.designpatterns.level02.integration.gateway.capability.ActionCompleter;
import com.designpatterns.level02.integration.gateway.capability.Authorizer;
import com.designpatterns.level02.integration.gateway.capability.DirectCharger;
import com.designpatterns.level02.model.Capability;
import com.designpatterns.level02.model.Payment;
import com.designpatterns.level02.model.PaymentAttempt;
import com.designpatterns.level02.model.PaymentStatus;
import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.model.exceptions.ConcurrentUpdateException;
import com.designpatterns.level02.model.exceptions.IllegalPaymentTransitionException;
import com.designpatterns.level02.persistence.PaymentAttemptRepository;
import com.designpatterns.level02.persistence.PaymentRepository;
import com.designpatterns.level02.service.merchant.MerchantService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Drives the AUTHORIZE step through provider attempts: write ahead, call, record. Falls back to the
 * next provider ONLY when the failure proves nothing was charged. Credentials are looked up per
 * attempt, for this payment's merchant at this provider.
 *
 * <p>ORCHESTRATOR: it coordinates several objects (payment, attempts, gateway) but makes no business
 * decision itself; the {@code Payment} decides what is allowed and {@code GatewayResultApplier} what an
 * answer means. FLOW A.9 – A.13.
 *
 * <p>Note the shape of every attempt: SAVE → CALL → SAVE, each save its own short transaction, the call
 * outside any transaction.
 */
@Service
public class AuthorizationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationOrchestrator.class);

    private final ProviderGateway gateways;
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final MerchantService merchants;
    private final Clock clock;

    public AuthorizationOrchestrator(ProviderGateway gateways, PaymentRepository payments, PaymentAttemptRepository attempts,
                               MerchantService merchants, Clock clock) {
        this.gateways = Objects.requireNonNull(gateways, "gateways");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.merchants = Objects.requireNonNull(merchants, "merchants");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Payment authorize(Payment payment, List<ProviderId> candidates) {
        for (ProviderId candidate : candidates) {
            // FLOW A.9: WRITE-AHEAD. Record "attempt X at provider Y" and commit BEFORE calling. If we crash
            // or time out during the call, reconciliation (FLOW D) can still ask Y about attempt X.
            PaymentAttempt attempt = PaymentAttempt.start(payment.id(), candidate, clock.instant());
            payment.startAttempt(attempt.attemptId(), candidate);
            Payment pending = writeAhead(payment, attempt);

            // FLOW A.10: the network call, OUTSIDE any database transaction.
            GatewayResult result = start(pending, attempt);

            // FLOW A.11 + A.12: apply the answer to the payment and save it.
            payment = finish(pending, attempt, result, false);
            // FLOW A.13: FALLBACK. Only "CREATED again" means the provider PROVED nothing was charged
            // (nothingCharged()). Authorized, declined, UNKNOWN, ... all stop here. After a timeout we
            // must NOT try another provider: the first one may have charged, the customer would pay twice.
            if (payment.status() != PaymentStatus.CREATED) {
                return payment;
            }
            log.warn("Nothing was charged by {}, trying the next provider", candidate);
        }
        payment.failed("Every provider failed without charging");
        return payments.save(payment);
    }

    /** Only the provider that started the payment can finish it: no fallback. */
    public Payment complete(Payment payment) {
        PaymentAttempt attempt = PaymentAttempt.start(payment.id(), payment.providerId(), clock.instant());
        payment.startCompletion(attempt.attemptId());
        Payment pending = writeAhead(payment, attempt);
        GatewayResult result = call(pending, attempt, ActionCompleter.class,
                (gateway, creds) -> gateway.completeAction(pending.providerRef(), creds));
        return finish(pending, attempt, result, true);
    }

    /**
     * FLOW A.10: hold the funds where the provider can; otherwise take them directly. The DESCRIPTOR
     * (what the provider says it can do) picks the capability interface to call.
     */
    private GatewayResult start(Payment pending, PaymentAttempt attempt) {
        if (gateways.descriptor(attempt.providerId()).supports(Capability.AUTHORIZE)) {
            return call(pending, attempt, Authorizer.class, (gateway, creds) -> gateway.authorize(request(pending, attempt, creds)));
        }
        return call(pending, attempt, DirectCharger.class, (gateway, creds) -> gateway.charge(request(pending, attempt, creds)));
    }

    private static AuthorizeRequest request(Payment payment, PaymentAttempt attempt, ProviderCredentials credentials) {
        return new AuthorizeRequest(payment.id(), payment.customerId(), payment.amount(), attempt.attemptId(), credentials);
    }

    private Payment writeAhead(Payment payment, PaymentAttempt attempt) {
        Payment saved = payments.save(payment);
        attempts.save(attempt);
        return saved;
    }

    private Payment finish(Payment pending, PaymentAttempt attempt, GatewayResult result, boolean completion) {
        Payment saved = applyAndSave(pending, result, completion);
        PaymentAttempt finished = attempt.finish(GatewayResultApplier.outcomeOf(result), clock.instant());
        attempts.save(finished);
        log.info("Attempt {} on {} for {}: {} in {} ms → payment {}", attempt.attemptId(), attempt.providerId(),
                pending.id(), finished.outcome(), finished.duration().toMillis(), saved.status());
        return saved;
    }

    /**
     * FLOW A.12: a webhook may have moved the payment while we waited (FLOW C can be faster than the
     * provider's own HTTP answer). Then our save loses the optimistic check: reload, apply on top of the
     * latest copy, or accept it if the webhook already brought the same news.
     */
    private Payment applyAndSave(Payment pending, GatewayResult result, boolean completion) {
        GatewayResultApplier.applyAuthorization(pending, result, completion);
        try {
            return payments.save(pending);
        } catch (ConcurrentUpdateException raced) {
            Payment latest = payments.find(pending.id()).orElseThrow();
            try {
                GatewayResultApplier.applyAuthorization(latest, result, completion);
                return payments.save(latest);
            } catch (IllegalPaymentTransitionException alreadyThere) {
                return latest;
            }
        }
    }

    private <C> GatewayResult call(Payment payment, PaymentAttempt attempt, Class<C> capability,
                                   BiFunction<C, ProviderCredentials, GatewayResult> call) {
        MDC.put("attemptId", attempt.attemptId()); // every log line of this call shows the attempt id
        try {
            ProviderCredentials creds = merchants.credentials(payment.merchantId(), attempt.providerId()).orElseThrow();
            return gateways.call(attempt.providerId(), capability, gateway -> call.apply(gateway, creds));
        } finally {
            MDC.remove("attemptId");
        }
    }


}
