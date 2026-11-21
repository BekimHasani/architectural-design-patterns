package com.designpatterns.level09.service.payment;

import com.designpatterns.level09.integration.gateway.FailureCategory;
import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.model.AttemptOutcome;
import com.designpatterns.level09.model.Payment;
import com.designpatterns.level09.model.PaymentStatus;

/**
 * THE ONE PLACE where a provider's answer changes a payment.
 *
 * <p>Level 8 had these {@code switch}es in three classes (orchestrator, operations, reconciliation). In
 * the layered design {@link GatewayResult} belongs to the integration layer below us, so it can grow
 * without the business noticing. Collecting every {@code switch} over it here gives one class to update
 * and one place to stop provider concepts leaking further into the service layer.
 *
 * <p>The switches are exhaustive over a SEALED interface: when integration adds a result type, this
 * class stops compiling, which is exactly where you want to be told.
 */
public final class GatewayResultApplier {

    private GatewayResultApplier() {
    }

    /**
     * An authorize, charge or "complete customer action" answer.
     *
     * @param completion true when finishing a customer action: a safe failure then means "still waiting
     *                   for the customer", not "try the next provider"
     */
    public static void applyAuthorization(Payment payment, GatewayResult result, boolean completion) {
        // FLOW A.11: TELL, DON'T ASK. We tell the payment what happened; it checks the transition is legal.
        // "case X x when condition" is a GUARDED PATTERN: the first matching case wins, top to bottom.
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

    /** A capture, cancel or refund answer. */
    public static void applyOperation(Payment payment, String operationId, GatewayResult result) {
        switch (result) {
            case GatewayResult.Done done -> payment.operationSucceeded(operationId, done.providerRef());
            case GatewayResult.Declined declined -> payment.operationFailed(operationId, declined.reason().name());
            case GatewayResult.Failed failed when failed.category().outcomeUnknown() -> payment.operationUnknown(operationId);
            case GatewayResult.Failed failed -> payment.operationFailed(operationId, failed.category().name());
            default -> payment.operationUnknown(operationId); // an answer that makes no sense for this call
        }
    }

    /**
     * Reconciliation: the provider's answer to "what happened to attempt X?".
     *
     * @return true if the answer settles the payment
     */
    public static boolean applyStatus(Payment payment, GatewayResult status) {
        switch (status) {
            case GatewayResult.Authorized a -> payment.authorized(a.providerRef());
            case GatewayResult.Charged c -> payment.charged(c.providerRef());
            case GatewayResult.Declined d -> payment.declined(d.reason());
            case GatewayResult.RequiresAction a -> payment.requiresAction(a.providerRef(), a.redirectUrl());
            case GatewayResult.Processing p when payment.status() == PaymentStatus.PROCESSING -> {
                return false;
            }
            case GatewayResult.Processing p -> payment.processing(p.providerRef());
            case GatewayResult.Failed f when f.category() == FailureCategory.NOT_PROCESSED ->
                    payment.failed("Provider has no record of the attempt: nothing was charged");
            case GatewayResult.Failed f -> {
                return false;
            }
            case GatewayResult.Done d -> {
                return false;
            }
        }
        return true;
    }

    /** How an attempt ended, for the attempt log. */
    public static AttemptOutcome outcomeOf(GatewayResult result) {
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

    /** Re-sending didn't settle it: we still don't know what the provider did. */
    public static boolean stillUnknown(GatewayResult result) {
        return result instanceof GatewayResult.Failed failed
                && (failed.category().outcomeUnknown() || failed.category().retryable());
    }
}
