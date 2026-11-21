package com.designpatterns.level09.service.payment;

import com.designpatterns.level09.persistence.config.PropertiesMerchantConfigRepository;
import com.designpatterns.level09.service.merchant.MerchantService;

import com.designpatterns.level09.integration.gateway.DefaultProviderGateway;
import com.designpatterns.level09.model.MerchantId;
import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.service.routing.ProviderRoutingService;
import com.designpatterns.level09.service.routing.rules.DefaultRule;
import com.designpatterns.level09.support.InMemoryPaymentAttemptRepository;
import com.designpatterns.level09.support.InMemoryPaymentRepository;
import com.designpatterns.level09.support.ScriptedGateway;


import java.time.Clock;
import java.util.List;
import java.util.Map;

/** Use-case wiring with in-memory storage and two scripted providers, tried in order. */
abstract class PlatformFixture {

    static final ProviderId FIRST = ProviderId.of("first");
    static final ProviderId SECOND = ProviderId.of("second");
    static final MerchantId ACME = new MerchantId("acme");
    static final MerchantId GLOBEX = new MerchantId("globex");

    final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
    final InMemoryPaymentAttemptRepository attempts = new InMemoryPaymentAttemptRepository();
    final MerchantService merchants = new MerchantService(new PropertiesMerchantConfigRepository(Map.of(
            "acme", Map.of("first", Map.of("api-key", "first-acme"), "second", Map.of("api-key", "second-acme")),
            "globex", Map.of("first", Map.of("api-key", "first-globex")))));

    PaymentService authorizePayment(ScriptedGateway first, ScriptedGateway second) {
        first.named(FIRST);
        second.named(SECOND);
        ProviderRoutingService router = new ProviderRoutingService(List.of(new DefaultRule(FIRST), new DefaultRule(SECOND)),
                List.of(first.descriptor(), second.descriptor()));
        DefaultProviderGateway registry = new DefaultProviderGateway(Map.of(FIRST, first, SECOND, second));
        AuthorizationOrchestrator orchestrator = new AuthorizationOrchestrator(registry, payments, attempts, merchants, Clock.systemUTC());
        return new PaymentService(router, orchestrator, payments, attempts, merchants, paymentOperations(first, second));
    }

    PaymentOperationService paymentOperations(ScriptedGateway first, ScriptedGateway second) {
        return new PaymentOperationService(payments, attempts, new DefaultProviderGateway(Map.of(FIRST, first, SECOND, second)), merchants);
    }
}
