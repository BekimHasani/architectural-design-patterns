package com.designpatterns.level09.service.reconciliation;

import com.designpatterns.level09.persistence.config.PropertiesMerchantConfigRepository;
import com.designpatterns.level09.service.merchant.MerchantService;

import com.designpatterns.level09.integration.gateway.DefaultProviderGateway;
import com.designpatterns.level09.integration.gateway.FailureCategory;
import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.model.CaptureMode;
import com.designpatterns.level09.model.MerchantId;
import com.designpatterns.level09.model.Money;
import com.designpatterns.level09.model.Payment;
import com.designpatterns.level09.model.PaymentId;
import com.designpatterns.level09.model.PaymentOperation;
import com.designpatterns.level09.model.PaymentStatus;
import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.service.payment.PaymentOperationService;
import com.designpatterns.level09.support.InMemoryPaymentAttemptRepository;
import com.designpatterns.level09.support.InMemoryPaymentRepository;
import com.designpatterns.level09.support.ScriptedGateway;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationServiceTest {

    private static final ProviderId STRIPE = ProviderId.of("stripe");
    private static final MerchantId ACME = new MerchantId("acme");

    private final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();

    @Test
    void anUnknownAuthorizationIsAskedAbout_byOurAttemptId() {
        ScriptedGateway gateway = new ScriptedGateway().withStatus(new GatewayResult.Authorized("pi_1"));
        PaymentId id = unknownAuthorization();

        assertThat(reconcile(gateway).run()).isEqualTo(new ReconciliationService.Report(1, 1));

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

    private ReconciliationService reconcile(ScriptedGateway gateway) {
        DefaultProviderGateway registry = new DefaultProviderGateway(Map.of(STRIPE, gateway));
        MerchantService merchants = new MerchantService(new PropertiesMerchantConfigRepository(Map.of("acme", Map.of("stripe", Map.of("api-key", "k")))));
        return new ReconciliationService(payments, registry, merchants,
                new PaymentOperationService(payments, new InMemoryPaymentAttemptRepository(), registry, merchants));
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
