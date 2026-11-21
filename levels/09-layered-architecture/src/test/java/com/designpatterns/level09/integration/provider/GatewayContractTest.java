package com.designpatterns.level09.integration.provider;

import com.designpatterns.level09.integration.gateway.AuthorizeRequest;
import com.designpatterns.level09.integration.gateway.DefaultProviderGateway;
import com.designpatterns.level09.integration.gateway.FailureCategory;
import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.integration.gateway.OperationRequest;
import com.designpatterns.level09.integration.gateway.ProviderAdapter;
import com.designpatterns.level09.integration.gateway.ProviderCredentials;
import com.designpatterns.level09.integration.gateway.ProviderDescriptor;
import com.designpatterns.level09.integration.gateway.capability.ActionCompleter;
import com.designpatterns.level09.integration.gateway.capability.Authorizer;
import com.designpatterns.level09.integration.gateway.capability.Canceller;
import com.designpatterns.level09.integration.gateway.capability.Capturer;
import com.designpatterns.level09.integration.gateway.capability.DirectCharger;
import com.designpatterns.level09.integration.gateway.capability.Refunder;
import com.designpatterns.level09.integration.support.ProviderHttp;
import com.designpatterns.level09.model.Capability;
import com.designpatterns.level09.model.Money;
import com.designpatterns.level09.model.PaymentId;

import com.designpatterns.simulators.BaseSimulator;
import com.designpatterns.simulators.Fault;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE PROVIDER CONTRACT KIT. A provider adds one subclass and gets exactly the tests its DECLARED
 * capabilities call for: nothing skipped, nothing to remember. Declare REFUND and the refund tests
 * appear, and fail if the code doesn't deliver. That is what keeps a descriptor honest.
 */
public abstract class GatewayContractTest<S extends BaseSimulator> {

    protected static final Money AMOUNT = Money.of(10000, "EUR");

    protected S sim;
    protected ProviderAdapter gateway;

    protected abstract S startSimulator() throws IOException;

    protected abstract ProviderAdapter gateway(S sim);

    protected abstract ProviderCredentials credentials();

    protected abstract ProviderCredentials badCredentials();

    /** Async-only providers: finish a pending payment, as the provider would. */
    protected void settle(String providerRef) {
        throw new UnsupportedOperationException("Implement settle() for an async provider");
    }

    protected static RestClient.Builder http() {
        return ProviderHttp.withTimeouts(RestClient.builder(), Duration.ofSeconds(1), Duration.ofMillis(500));
    }

    @TestFactory
    List<DynamicTest> contract() throws IOException {
        S probe = startSimulator();
        ProviderDescriptor d = gateway(probe).descriptor();
        probe.close();

        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "startFaults: 503 is unavailable", this::http503);
        add(tests, "startFaults: 500 is unknown", this::http500);
        add(tests, "startFaults: 429 is rate limited", this::http429);
        add(tests, "startFaults: unreachable is unavailable", this::unreachable);
        add(tests, "startFaults: bad credentials are our problem", this::badCredentialsFail);
        add(tests, "startFaults: timeout is unknown", this::timeout);
        add(tests, "decline is a decline", this::decline);
        add(tests, "same attempt twice is idempotent", this::idempotent);
        add(tests, "after a lost answer, getStatus tells what happened", this::lostAnswer);
        add(tests, "getStatus of an unseen attempt is NOT_PROCESSED", this::neverSeen);
        add(tests, "the interfaces match the descriptor", this::descriptorIsHonest);
        if (d.supports(Capability.AUTHORIZE)) {
            add(tests, "authorize holds the money: partial capture moves that amount", this::partialCapture);
            add(tests, "cancel releases the hold", this::cancel);
        }
        if (d.supports(Capability.CUSTOMER_ACTION)) {
            add(tests, "customer action, then authorized", this::customerAction);
        }
        if (d.confirmation() != ProviderDescriptor.Confirmation.SYNC) {
            add(tests, "async acceptance is Processing", this::async);
        }
        if (d.supports(Capability.REFUND)) {
            if (d.refundTerms().partialAllowed()) {
                add(tests, "a partial refund moves exactly that amount", this::partialRefund);
            }
            add(tests, "refunding too much is refused", this::refundTooMuch);
            add(tests, "re-sending a lost refund moves the money once", this::resendRefund);
        }
        return tests;
    }

    private void add(List<DynamicTest> tests, String name, Runnable body) {
        tests.add(DynamicTest.dynamicTest(name, () -> {
            sim = startSimulator();
            gateway = gateway(sim);
            try {
                body.run();
            } finally {
                sim.close();
            }
        }));
    }

    // ---- what every provider must do ----------------------------------------------------------

    private void http503() {
        sim.injectFault(Fault.HTTP_503, 1);
        assertFailure(start(request()), FailureCategory.PROVIDER_UNAVAILABLE);
    }

    private void http500() {
        sim.injectFault(Fault.HTTP_500, 1);
        assertFailure(start(request()), FailureCategory.OUTCOME_UNKNOWN);
    }

    private void http429() {
        sim.injectFault(Fault.HTTP_429, 1);
        assertFailure(start(request()), FailureCategory.RATE_LIMITED);
    }

    private void unreachable() {
        sim.close();
        assertFailure(start(request()), FailureCategory.PROVIDER_UNAVAILABLE);
    }

    private void badCredentialsFail() {
        assertFailure(start(new AuthorizeRequest(PaymentId.newId(), "c-1", AMOUNT, newAttemptId(), badCredentials())),
                FailureCategory.INVALID_REQUEST);
    }

    private void timeout() {
        sim.injectFault(Fault.TIMEOUT, 1);
        assertFailure(start(request()), FailureCategory.OUTCOME_UNKNOWN);
    }

    private void decline() {
        sim.injectFault(Fault.DECLINE, 1);
        GatewayResult result = start(request());
        if (result instanceof GatewayResult.RequiresAction action) { // PayPal: the decline comes at capture
            sim.completeCustomerAction(action.providerRef());
            String ref = ((GatewayResult.Authorized) ((ActionCompleter) gateway).completeAction(action.providerRef(), credentials())).providerRef();
            result = ((Capturer) gateway).capture(operation(ref, AMOUNT));
        }
        assertThat(result).isInstanceOf(GatewayResult.Declined.class);
        assertThat(sim.ledger()).isEmpty();
    }

    private void idempotent() {
        AuthorizeRequest request = request();
        assertThat(start(request)).isEqualTo(start(request));
        assertThat(sim.ledger()).hasSizeLessThanOrEqualTo(1);
    }

    private void lostAnswer() {
        sim.injectFault(Fault.DROP_AFTER_COMMIT, 1);
        AuthorizeRequest request = request();

        assertFailure(start(request), FailureCategory.OUTCOME_UNKNOWN);

        assertThat(gateway.getStatus(request.attemptId(), credentials())).isInstanceOfAny(GatewayResult.Authorized.class,
                GatewayResult.Charged.class, GatewayResult.RequiresAction.class, GatewayResult.Processing.class);
    }

    private void neverSeen() {
        assertFailure(gateway.getStatus("att_never-sent", credentials()), FailureCategory.NOT_PROCESSED);
    }

    private void descriptorIsHonest() {
        DefaultProviderGateway.requireHonestDescriptor(gateway);
    }

    // ---- by capability ------------------------------------------------------------------------

    private void partialCapture() {
        String ref = authorized();
        assertThat(sim.ledger()).as("nothing charged yet").isEmpty();

        assertThat(((Capturer) gateway).capture(operation(ref, Money.of(6000, "EUR")))).isInstanceOf(GatewayResult.Done.class);

        assertThat(sim.ledger()).singleElement().satisfies(e -> assertThat(e.amountMinor()).isEqualTo(6000));
    }

    private void cancel() {
        String ref = authorized();

        assertThat(((Canceller) gateway).cancel(new OperationRequest(ref, null, newOperationId(), credentials())))
                .isInstanceOf(GatewayResult.Done.class);

        assertThat(((Capturer) gateway).capture(operation(ref, AMOUNT))).as("gone").isInstanceOf(GatewayResult.Failed.class);
        assertThat(sim.ledger()).isEmpty();
    }

    private void customerAction() {
        sim.injectFault(Fault.REQUIRES_ACTION, 1);
        GatewayResult started = start(request());
        assertThat(started).isInstanceOf(GatewayResult.RequiresAction.class);
        String ref = ((GatewayResult.RequiresAction) started).providerRef();
        ActionCompleter completer = (ActionCompleter) gateway;

        assertThat(completer.completeAction(ref, credentials())).as("too early").isInstanceOf(GatewayResult.RequiresAction.class);
        sim.completeCustomerAction(ref);
        assertThat(completer.completeAction(ref, credentials())).isEqualTo(new GatewayResult.Authorized(ref));
    }

    private void async() {
        sim.injectFault(Fault.ASYNC, 1);
        assertThat(start(request())).isInstanceOf(GatewayResult.Processing.class);
        assertThat(sim.ledger()).isEmpty();
    }

    private void partialRefund() {
        assertThat(((Refunder) gateway).refund(operation(captured(), Money.of(3000, "EUR")))).isInstanceOf(GatewayResult.Done.class);
        assertThat(sim.refunds()).singleElement().satisfies(e -> assertThat(e.amountMinor()).isEqualTo(3000));
    }

    private void refundTooMuch() {
        assertFailure(((Refunder) gateway).refund(operation(captured(), Money.of(AMOUNT.amountMinor() + 1, "EUR"))),
                FailureCategory.INVALID_REQUEST);
        assertThat(sim.refunds()).isEmpty();
    }

    private void resendRefund() {
        String target = captured();
        sim.injectFault(Fault.DROP_AFTER_COMMIT, 1);
        OperationRequest refund = operation(target, AMOUNT);

        assertFailure(((Refunder) gateway).refund(refund), FailureCategory.OUTCOME_UNKNOWN);
        assertThat(sim.refunds()).as("it happened").hasSize(1);

        assertThat(((Refunder) gateway).refund(refund)).isInstanceOf(GatewayResult.Done.class);
        assertThat(sim.refunds()).as("once").hasSize(1);
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** The provider's way of starting a payment. */
    private GatewayResult start(AuthorizeRequest request) {
        return gateway instanceof Authorizer authorizer ? authorizer.authorize(request) : ((DirectCharger) gateway).charge(request);
    }

    private String authorized() {
        GatewayResult result = start(request());
        if (result instanceof GatewayResult.RequiresAction action) {
            sim.completeCustomerAction(action.providerRef());
            result = ((ActionCompleter) gateway).completeAction(action.providerRef(), credentials());
        }
        return ((GatewayResult.Authorized) result).providerRef();
    }

    /** Brings a payment to "money moved", and returns what a refund must point at. */
    private String captured() {
        if (gateway instanceof Capturer capturer) {
            return ((GatewayResult.Done) capturer.capture(operation(authorized(), AMOUNT))).providerRef();
        }
        GatewayResult result = start(request());
        if (result instanceof GatewayResult.Processing processing) {
            settle(processing.providerRef());
            return processing.providerRef();
        }
        return ((GatewayResult.Charged) result).providerRef();
    }

    private AuthorizeRequest request() {
        return new AuthorizeRequest(PaymentId.newId(), "c-1", AMOUNT, newAttemptId(), credentials());
    }

    private OperationRequest operation(String ref, Money amount) {
        return new OperationRequest(ref, amount, newOperationId(), credentials());
    }

    private static void assertFailure(GatewayResult result, FailureCategory category) {
        assertThat(result).isInstanceOfSatisfying(GatewayResult.Failed.class, f -> assertThat(f.category()).isEqualTo(category));
    }

    private static String newAttemptId() {
        return "att_" + UUID.randomUUID();
    }

    private static String newOperationId() {
        return "op_" + UUID.randomUUID();
    }
}
