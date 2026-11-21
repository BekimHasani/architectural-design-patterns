package com.designpatterns.level08.payments.application.reconciliation;

import com.designpatterns.level08.merchants.ConfiguredMerchants;
import com.designpatterns.level08.payments.application.commands.PaymentOperations;
import com.designpatterns.level08.payments.application.ports.FailureCategory;
import com.designpatterns.level08.payments.application.ports.GatewayResult;
import com.designpatterns.level08.payments.domain.CaptureMode;
import com.designpatterns.level08.payments.domain.MerchantId;
import com.designpatterns.level08.payments.domain.Money;
import com.designpatterns.level08.payments.domain.Payment;
import com.designpatterns.level08.payments.domain.PaymentId;
import com.designpatterns.level08.payments.domain.PaymentOperation;
import com.designpatterns.level08.payments.domain.PaymentStatus;
import com.designpatterns.level08.payments.domain.ProviderId;
import com.designpatterns.level08.providers.InMemoryGatewayRegistry;
import com.designpatterns.level08.support.InMemoryPaymentAttemptLog;
import com.designpatterns.level08.support.InMemoryPaymentRepository;
import com.designpatterns.level08.support.ScriptedGateway;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReconcileUnknownPaymentsTest {

    private static final ProviderId STRIPE = ProviderId.of("stripe");
    private static final MerchantId ACME = new MerchantId("acme");

    private final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();

    @Test
    void anUnknownAuthorizationIsAskedAbout_byOurAttemptId() {
        ScriptedGateway gateway = new ScriptedGateway().withStatus(new GatewayResult.Authorized("pi_1"));
        PaymentId id = unknownAuthorization();

        assertThat(reconcile(gateway).run()).isEqualTo(new ReconcileUnknownPayments.Report(1, 1));

        assertThat(gateway.statusLookups).containsExactly("att_lost");
        assertThat(gateway.authorizations).as("never re-sent").isEmpty();
        assertThat(payments.find(id).orElseThrow().status()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    void ifTheLookupFailsThePaymentStaysUnknown() {
        PaymentId id = unknownAuthorization();

        reconcile(new ScriptedGateway().withStatus(new GatewayResult.Failed(FailureCategory.PROVIDER_UNAVAILABLE, "503"))).run();

        assertThat(payments.find(id).orElseThrow().status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    @Test
    void anUnknownRefundIsResentWithTheSameOperationId() {
        ScriptedGateway gateway = new ScriptedGateway(new GatewayResult.Done("pi_1"));
        PaymentId id = capturedWithUnknownRefund();

        reconcile(gateway).run();

        assertThat(gateway.operations).singleElement().satisfies(op -> assertThat(op.operationId()).isEqualTo("op_lost"));
        Payment payment = payments.find(id).orElseThrow();
        assertThat(payment.findOperation("op_lost").orElseThrow().status()).isEqualTo(PaymentOperation.Status.SUCCEEDED);
        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void anAuthorizationThatArrivedLateIsCapturedIfTheMerchantWantedAutomatic() {
        ScriptedGateway gateway = new ScriptedGateway(new GatewayResult.Done("pi_1"));
        Payment payment = Payment.create(PaymentId.newId(), ACME, "c-1", Money.of(1000, "EUR"), CaptureMode.AUTOMATIC);
        payment.startAttempt("att_1", STRIPE);
        payment.authorized("pi_1");
        PaymentId id = payments.save(payment).id();

        reconcile(gateway).run();

        assertThat(payments.find(id).orElseThrow().status()).isEqualTo(PaymentStatus.CAPTURED);
    }

    private ReconcileUnknownPayments reconcile(ScriptedGateway gateway) {
        InMemoryGatewayRegistry registry = new InMemoryGatewayRegistry(Map.of(STRIPE, gateway));
        ConfiguredMerchants merchants = new ConfiguredMerchants(Map.of("acme", Map.of("stripe", Map.of("api-key", "k"))));
        return new ReconcileUnknownPayments(payments, registry, merchants,
                new PaymentOperations(payments, new InMemoryPaymentAttemptLog(), registry, merchants));
    }

    private PaymentId unknownAuthorization() {
        Payment payment = Payment.create(PaymentId.newId(), ACME, "c-1", Money.of(1000, "EUR"));
        payment.startAttempt("att_lost", STRIPE);
        payment.outcomeUnknown();
        return payments.save(payment).id();
    }

    private PaymentId capturedWithUnknownRefund() {
        Payment payment = Payment.create(PaymentId.newId(), ACME, "c-1", Money.of(1000, "EUR"));
        payment.startAttempt("att_1", STRIPE);
        payment.charged("pi_1");
        payment.startRefund("op_lost", null);
        payment.operationUnknown("op_lost");
        return payments.save(payment).id();
    }
}
