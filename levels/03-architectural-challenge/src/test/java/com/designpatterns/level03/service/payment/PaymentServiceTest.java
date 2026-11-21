package com.designpatterns.level03.service.payment;

import com.designpatterns.level03.integration.gateway.GatewayResult;
import com.designpatterns.level03.integration.gateway.GatewayResult.FailureKind;
import com.designpatterns.level03.integration.gateway.ProviderGateways;
import com.designpatterns.level03.model.Money;
import com.designpatterns.level03.model.Payment;
import com.designpatterns.level03.model.PaymentStatus;
import com.designpatterns.level03.model.exceptions.PaymentNotFoundException;
import com.designpatterns.level03.model.exceptions.PaymentRuleViolationException;
import com.designpatterns.level03.service.routing.RoutingService;
import com.designpatterns.level03.support.InMemoryPaymentRepository;
import com.designpatterns.level03.support.ScriptedGateway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The use cases with in-memory fakes: no Spring, no HTTP, no database. */
class PaymentServiceTest {

    private static final ChargeCommand TEN_EURO = new ChargeCommand("acme", "cus_1", Money.of(1_000, "EUR"));

    private final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();

    private PaymentService serviceWith(ScriptedGateway... gateways) {
        List<String> order = java.util.Arrays.stream(gateways).map(ScriptedGateway::id).toList();
        return new PaymentService(payments, new ProviderGateways(List.of(gateways)), new RoutingService(order, Map.of()));
    }

    @Test
    void aSuccessfulChargeIsCapturedAtTheFirstProvider() {
        ScriptedGateway first = new ScriptedGateway("first", new GatewayResult.Succeeded("ref_1"));
        ScriptedGateway second = new ScriptedGateway("second");

        Payment payment = serviceWith(first, second).charge(TEN_EURO);

        assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.providerId()).isEqualTo("first");
        assertThat(payment.providerReference()).isEqualTo("ref_1");
        assertThat(payments.rows).containsKey(payment.id());
        assertThat(second.charges).isEmpty();
    }

    @Test
    void eachAttemptHasItsOwnIdAndTheFullAmount() {
        ScriptedGateway first = new ScriptedGateway("first", new GatewayResult.Succeeded("ref_1"));

        serviceWith(first).charge(TEN_EURO);

        assertThat(first.charges).singleElement().satisfies(request -> {
            assertThat(request.attemptId()).startsWith("att_");
            assertThat(request.amount()).isEqualTo(Money.of(1_000, "EUR"));
        });
    }

    @Test
    void aDeclineIsFinalAndNeverTriedElsewhere() {
        ScriptedGateway first = new ScriptedGateway("first", new GatewayResult.Declined("insufficient_funds"));
        ScriptedGateway second = new ScriptedGateway("second");

        Payment payment = serviceWith(first, second).charge(TEN_EURO);

        assertThat(payment.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.failureReason()).isEqualTo("insufficient_funds");
        assertThat(second.charges).isEmpty();
    }

    @Test
    void whenNothingWasChargedTheNextProviderIsTried() {
        ScriptedGateway first = new ScriptedGateway("first", new GatewayResult.Failed(FailureKind.NOTHING_CHARGED, "HTTP 503"));
        ScriptedGateway second = new ScriptedGateway("second", new GatewayResult.Succeeded("ref_2"));

        Payment payment = serviceWith(first, second).charge(TEN_EURO);

        assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.providerId()).isEqualTo("second");
    }

    @Test
    void whenNoProviderCanChargeThePaymentFails() {
        ScriptedGateway first = new ScriptedGateway("first", new GatewayResult.Failed(FailureKind.NOTHING_CHARGED, "HTTP 503"));
        ScriptedGateway second = new ScriptedGateway("second", new GatewayResult.Failed(FailureKind.NOTHING_CHARGED, "refused"));

        Payment payment = serviceWith(first, second).charge(TEN_EURO);

        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.failureReason()).contains("second");
    }

    @Test
    @Tag("challenge")
    void aLostResponseIsNeverRetriedAtAnotherProvider() {
        ScriptedGateway first = new ScriptedGateway("first", new GatewayResult.Failed(FailureKind.OUTCOME_UNKNOWN, "read timeout"));
        ScriptedGateway second = new ScriptedGateway("second", new GatewayResult.Succeeded("ref_2"));

        Payment payment = serviceWith(first, second).charge(TEN_EURO);

        assertThat(second.charges).as("first may already have charged the customer").isEmpty();
        assertThat(payment.status()).isEqualTo(PaymentStatus.OUTCOME_UNKNOWN);
        assertThat(payment.providerId()).isEqualTo("first");
    }

    @Test
    void aRefundGoesToTheProviderThatCharged() {
        ScriptedGateway first = new ScriptedGateway("first", new GatewayResult.Succeeded("ref_1"));
        PaymentService service = serviceWith(first);
        Payment payment = service.charge(TEN_EURO);

        Payment refunded = service.refund("acme", payment.id(), Money.of(400, "EUR"), "damaged");

        assertThat(refunded.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(refunded.refunded()).isEqualTo(Money.of(400, "EUR"));
        assertThat(first.refunds).singleElement().satisfies(request -> {
            assertThat(request.providerReference()).isEqualTo("ref_1");
            assertThat(request.amount()).isEqualTo(Money.of(400, "EUR"));
        });
    }

    @Test
    void aRefundTheProviderRejectsChangesNothing() {
        ScriptedGateway first = new ScriptedGateway("first", new GatewayResult.Succeeded("ref_1"))
                .refundsWith(new GatewayResult.Failed(FailureKind.NOTHING_CHARGED, "HTTP 400"));
        PaymentService service = serviceWith(first);
        Payment payment = service.charge(TEN_EURO);

        assertThatThrownBy(() -> service.refund("acme", payment.id(), Money.of(400, "EUR"), null))
                .isInstanceOf(PaymentRuleViolationException.class);
        assertThat(payments.rows.get(payment.id()).status()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void aDeclinedPaymentIsRefusedBeforeAnyProviderCall() {
        ScriptedGateway first = new ScriptedGateway("first", new GatewayResult.Declined("card_declined"));
        PaymentService service = serviceWith(first);
        Payment payment = service.charge(TEN_EURO);

        assertThatThrownBy(() -> service.refund("acme", payment.id(), Money.of(400, "EUR"), null))
                .isInstanceOf(PaymentRuleViolationException.class);
        assertThat(first.refunds).isEmpty();
    }

    @Test
    void anotherMerchantsPaymentDoesNotExist() {
        ScriptedGateway first = new ScriptedGateway("first", new GatewayResult.Succeeded("ref_1"));
        PaymentService service = serviceWith(first);
        Payment payment = service.charge(TEN_EURO);

        assertThatThrownBy(() -> service.refund("globex", payment.id(), Money.of(400, "EUR"), null))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThat(first.refunds).isEmpty();
    }
}
