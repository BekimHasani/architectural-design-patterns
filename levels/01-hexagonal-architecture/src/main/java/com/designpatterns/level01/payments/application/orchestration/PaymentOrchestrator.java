package com.designpatterns.level01.payments.application.orchestration;

import com.designpatterns.level01.payments.application.capabilities.ActionCompleter;
import com.designpatterns.level01.payments.application.capabilities.Authorizer;
import com.designpatterns.level01.payments.application.capabilities.Capability;
import com.designpatterns.level01.payments.application.capabilities.DirectCharger;
import com.designpatterns.level01.payments.application.ports.AuthorizeRequest;
import com.designpatterns.level01.payments.application.ports.ConcurrentUpdateException;
import com.designpatterns.level01.payments.application.ports.GatewayRegistry;
import com.designpatterns.level01.payments.application.ports.GatewayResult;
import com.designpatterns.level01.payments.application.ports.MerchantCredentials;
import com.designpatterns.level01.payments.application.ports.PaymentAttemptLog;
import com.designpatterns.level01.payments.application.ports.PaymentRepository;
import com.designpatterns.level01.payments.application.ports.ProviderCredentials;
import com.designpatterns.level01.payments.domain.AttemptOutcome;
import com.designpatterns.level01.payments.domain.IllegalPaymentTransitionException;
import com.designpatterns.level01.payments.domain.Payment;
import com.designpatterns.level01.payments.domain.PaymentAttempt;
import com.designpatterns.level01.payments.domain.PaymentStatus;
import com.designpatterns.level01.payments.domain.ProviderId;
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
 */
public final class PaymentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrchestrator.class);

    private final GatewayRegistry gateways;
    private final PaymentRepository payments;
    private final PaymentAttemptLog attempts;
    private final MerchantCredentials credentials;
    private final Clock clock;

    public PaymentOrchestrator(GatewayRegistry gateways, PaymentRepository payments, PaymentAttemptLog attempts,
                               MerchantCredentials credentials, Clock clock) {
        this.gateways = Objects.requireNonNull(gateways, "gateways");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Payment authorize(Payment payment, List<ProviderId> candidates) {
        for (ProviderId candidate : candidates) {
            PaymentAttempt attempt = PaymentAttempt.start(payment.id(), candidate, clock.instant());
            payment.startAttempt(attempt.attemptId(), candidate);
            Payment pending = writeAhead(payment, attempt);

            GatewayResult result = start(pending, attempt);

            payment = finish(pending, attempt, result, false);
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

    /** Hold the funds where the provider can; otherwise take them directly. */
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
        PaymentAttempt finished = attempt.finish(outcomeOf(result), clock.instant());
        attempts.save(finished);
        log.info("Attempt {} on {} for {}: {} in {} ms → payment {}", attempt.attemptId(), attempt.providerId(),
                pending.id(), finished.outcome(), finished.duration().toMillis(), saved.status());
        return saved;
    }

    /** A webhook may have moved the payment while we waited. Apply on top of it, or accept it. */
    private Payment applyAndSave(Payment pending, GatewayResult result, boolean completion) {
        apply(pending, result, completion);
        try {
            return payments.save(pending);
        } catch (ConcurrentUpdateException raced) {
            Payment latest = payments.find(pending.id()).orElseThrow();
            try {
                apply(latest, result, completion);
                return payments.save(latest);
            } catch (IllegalPaymentTransitionException alreadyThere) {
                return latest;
            }
        }
    }

    private <C> GatewayResult call(Payment payment, PaymentAttempt attempt, Class<C> capability,
                                   BiFunction<C, ProviderCredentials, GatewayResult> call) {
        MDC.put("attemptId", attempt.attemptId());
        try {
            ProviderCredentials creds = credentials.find(payment.merchantId(), attempt.providerId()).orElseThrow();
            return gateways.call(attempt.providerId(), capability, gateway -> call.apply(gateway, creds));
        } finally {
            MDC.remove("attemptId");
        }
    }

    private static void apply(Payment payment, GatewayResult result, boolean completion) {
        switch (result) {
            case GatewayResult.Authorized a -> payment.authorized(a.providerRef());
            case GatewayResult.Charged c -> payment.charged(c.providerRef());
            case GatewayResult.Declined d -> payment.declined(d.reason());
            case GatewayResult.RequiresAction a -> payment.requiresAction(a.providerRef(), a.redirectUrl());
            case GatewayResult.Processing p -> payment.processing(p.providerRef());
            case GatewayResult.Done d -> payment.failed("Unexpected answer from provider");
            case GatewayResult.Failed f when f.category().outcomeUnknown() -> payment.outcomeUnknown();
            case GatewayResult.Failed f when f.category().safeToFallback() -> {
                if (completion) {
                    payment.completionNotDone();
                } else {
                    payment.nothingCharged();
                }
            }
            case GatewayResult.Failed f -> payment.failed(f.category().name());
        }
    }

    private static AttemptOutcome outcomeOf(GatewayResult result) {
        return switch (result) {
            case GatewayResult.Authorized a -> AttemptOutcome.AUTHORIZED;
            case GatewayResult.Charged c -> AttemptOutcome.CAPTURED;
            case GatewayResult.Done d -> AttemptOutcome.CAPTURED;
            case GatewayResult.Declined d -> AttemptOutcome.DECLINED;
            case GatewayResult.RequiresAction a -> AttemptOutcome.REQUIRES_ACTION;
            case GatewayResult.Processing p -> AttemptOutcome.PROCESSING;
            case GatewayResult.Failed f -> switch (f.category()) {
                case INVALID_REQUEST, NOT_PROCESSED -> AttemptOutcome.FAILED_INVALID_REQUEST;
                case RATE_LIMITED, PROVIDER_UNAVAILABLE -> AttemptOutcome.FAILED_NOTHING_CHARGED;
                case OUTCOME_UNKNOWN -> AttemptOutcome.OUTCOME_UNKNOWN;
            };
        };
    }
}
