package com.designpatterns.level01.payments.application.commands;

import com.designpatterns.level01.payments.application.capabilities.Capability;
import com.designpatterns.level01.payments.application.capabilities.DirectCharger;
import com.designpatterns.level01.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level01.payments.application.capabilities.ProviderDescriptor;
import com.designpatterns.level01.payments.application.ports.AuthorizeRequest;
import com.designpatterns.level01.payments.application.ports.ConcurrentUpdateException;
import com.designpatterns.level01.payments.application.ports.FailureCategory;
import com.designpatterns.level01.payments.application.ports.GatewayResult;
import com.designpatterns.level01.payments.application.ports.ProviderCredentials;
import com.designpatterns.level01.payments.domain.Money;
import com.designpatterns.level01.payments.domain.Payment;
import com.designpatterns.level01.payments.domain.PaymentId;
import com.designpatterns.level01.payments.domain.PaymentOperation;
import com.designpatterns.level01.payments.domain.PaymentRuleViolationException;
import com.designpatterns.level01.payments.domain.PaymentStatus;
import com.designpatterns.level01.payments.domain.RefundTerms;
import com.designpatterns.level01.providers.InMemoryGatewayRegistry;
import com.designpatterns.level01.support.ScriptedGateway;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentOperationsTest extends PlatformFixture {

    private static final Money AMOUNT = Money.of(10000, "EUR");

    private PaymentId authorizedOn(ScriptedGateway gateway) {
        return authorizePayment(gateway, new ScriptedGateway()).authorize(ACME, "c-1", AMOUNT).payment().id();
    }

    @Test
    void captureGoesToTheProviderThatTookThePayment() {
        ScriptedGateway fallback = new ScriptedGateway(new GatewayResult.Authorized("ref-2"), new GatewayResult.Done("ref-2"));
        ScriptedGateway dead = new ScriptedGateway(new GatewayResult.Failed(FailureCategory.PROVIDER_UNAVAILABLE, "503"));
        PaymentId id = authorizePayment(dead, fallback).authorize(ACME, "c-1", AMOUNT).payment().id();

        paymentOperations(dead, fallback).capture(ACME, id, Money.of(6000, "EUR"));

        assertThat(fallback.operations).singleElement().satisfies(op -> {
            assertThat(op.amount()).isEqualTo(Money.of(6000, "EUR"));
            assertThat(op.providerRef()).isEqualTo("ref-2");
            assertThat(op.credentials().require("api-key")).isEqualTo("second-acme");
        });
        assertThat(dead.operations).isEmpty();
    }

    @Test
    void theOperationIsStoredBeforeTheProviderIsCalled_andItsIdIsTheIdempotencyKey() {
        ScriptedGateway gateway = new ScriptedGateway(new GatewayResult.Authorized("ref"), new GatewayResult.Done("ref"));
        PaymentId id = authorizedOn(gateway);

        Payment payment = paymentOperations(gateway, new ScriptedGateway()).capture(ACME, id, null).payment();

        assertThat(gateway.operations.get(0).operationId()).isEqualTo(payment.operations().get(0).operationId());
        assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void aRuleViolationNeverReachesTheProvider() {
        ScriptedGateway gateway = new ScriptedGateway(new GatewayResult.Authorized("ref"));
        PaymentId id = authorizedOn(gateway);

        assertThatThrownBy(() -> paymentOperations(gateway, new ScriptedGateway()).capture(ACME, id, Money.of(10001, "EUR")))
                .isInstanceOf(PaymentRuleViolationException.class);
        assertThat(gateway.operations).isEmpty();
    }

    @Test
    void aSafeFailureReleasesTheOperation() {
        ScriptedGateway gateway = new ScriptedGateway(new GatewayResult.Authorized("ref"),
                new GatewayResult.Failed(FailureCategory.PROVIDER_UNAVAILABLE, "503"), new GatewayResult.Done("ref"));
        PaymentId id = authorizedOn(gateway);
        PaymentOperations operations = paymentOperations(gateway, new ScriptedGateway());

        assertThat(operations.capture(ACME, id, null).payment().status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(operations.capture(ACME, id, null).payment().status()).as("may try again").isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void anUnknownOutcomeIsKeptAsUnknown() {
        ScriptedGateway gateway = new ScriptedGateway(new GatewayResult.Authorized("ref"),
                new GatewayResult.Failed(FailureCategory.OUTCOME_UNKNOWN, "timeout"));
        PaymentId id = authorizedOn(gateway);

        Payment payment = paymentOperations(gateway, new ScriptedGateway()).capture(ACME, id, null).payment();

        assertThat(payment.operations().get(0).status()).isEqualTo(PaymentOperation.Status.UNKNOWN);
    }

    @Test
    void anotherMerchantCannotTouchThePayment() {
        ScriptedGateway gateway = new ScriptedGateway(new GatewayResult.Authorized("ref"));
        PaymentId id = authorizedOn(gateway);

        assertThatThrownBy(() -> paymentOperations(gateway, new ScriptedGateway()).capture(GLOBEX, id, null))
                .isInstanceOf(UnknownPaymentException.class);
    }

    /** A provider without Refunder: no method to call, no request to send, a clear answer. */
    @Test
    void aRefundAtAProviderThatCantRefundIsRefusedInOurWords() {
        InMemoryGatewayRegistry registry = new InMemoryGatewayRegistry(Map.of(FIRST, new ChargeOnly()));
        Payment payment = Payment.create(PaymentId.newId(), ACME, "c-1", AMOUNT);
        payment.startAttempt("att_1", FIRST);
        payment.charged("ref");
        PaymentId id = payments.save(payment).id();
        PaymentOperations operations = new PaymentOperations(payments, attempts, registry, merchants);

        assertThatThrownBy(() -> operations.refund(ACME, id, null))
                .isInstanceOf(OperationNotSupportedException.class)
                .hasMessageContaining("does not support refund");
        assertThat(payments.find(id).orElseThrow().operations()).as("nothing recorded").hasSize(1);
        assertThat(operations.allowedActions(payments.find(id).orElseThrow())).isEmpty();
    }

    private static final class ChargeOnly implements ProviderAdapter, DirectCharger {

        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(FIRST, Set.of(Capability.CHARGE), Set.of(Currency.getInstance("EUR")),
                    RefundTerms.ANY, ProviderDescriptor.Confirmation.SYNC);
        }

        @Override
        public GatewayResult charge(AuthorizeRequest request) {
            return new GatewayResult.Charged("ref");
        }

        @Override
        public GatewayResult getStatus(String attemptId, ProviderCredentials credentials) {
            return new GatewayResult.Charged("ref");
        }
    }

    /** Two refunds read the same version: the second loses at write-ahead, before reaching the provider. */
    @Test
    void aStaleCopyCannotReserveTheSameMoneyTwice() {
        ScriptedGateway gateway = new ScriptedGateway(new GatewayResult.Authorized("ref"), new GatewayResult.Done("ref"));
        PaymentId id = authorizedOn(gateway);
        paymentOperations(gateway, new ScriptedGateway()).capture(ACME, id, null);
        Payment first = payments.find(id).orElseThrow();
        Payment second = payments.find(id).orElseThrow();

        first.startRefund("op_a", AMOUNT);
        payments.save(first);
        second.startRefund("op_b", AMOUNT);

        assertThatThrownBy(() -> payments.save(second)).isInstanceOf(ConcurrentUpdateException.class);
    }
}
