package com.designpatterns.level08.payments.application.commands;

import com.designpatterns.level08.merchants.ConfiguredMerchants;
import com.designpatterns.level08.payments.application.orchestration.PaymentOrchestrator;
import com.designpatterns.level08.payments.application.routing.ProviderRouter;
import com.designpatterns.level08.payments.application.routing.rules.DefaultRule;
import com.designpatterns.level08.payments.domain.MerchantId;
import com.designpatterns.level08.payments.domain.ProviderId;
import com.designpatterns.level08.providers.InMemoryGatewayRegistry;
import com.designpatterns.level08.support.InMemoryPaymentAttemptLog;
import com.designpatterns.level08.support.InMemoryPaymentRepository;
import com.designpatterns.level08.support.ScriptedGateway;

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
    final InMemoryPaymentAttemptLog attempts = new InMemoryPaymentAttemptLog();
    final ConfiguredMerchants merchants = new ConfiguredMerchants(Map.of(
            "acme", Map.of("first", Map.of("api-key", "first-acme"), "second", Map.of("api-key", "second-acme")),
            "globex", Map.of("first", Map.of("api-key", "first-globex"))));

    AuthorizePayment authorizePayment(ScriptedGateway first, ScriptedGateway second) {
        first.named(FIRST);
        second.named(SECOND);
        ProviderRouter router = new ProviderRouter(List.of(new DefaultRule(FIRST), new DefaultRule(SECOND)),
                List.of(first.descriptor(), second.descriptor()));
        InMemoryGatewayRegistry registry = new InMemoryGatewayRegistry(Map.of(FIRST, first, SECOND, second));
        PaymentOrchestrator orchestrator = new PaymentOrchestrator(registry, payments, attempts, merchants, Clock.systemUTC());
        return new AuthorizePayment(router, orchestrator, payments, attempts, merchants, paymentOperations(first, second));
    }

    PaymentOperations paymentOperations(ScriptedGateway first, ScriptedGateway second) {
        return new PaymentOperations(payments, attempts, new InMemoryGatewayRegistry(Map.of(FIRST, first, SECOND, second)), merchants);
    }
}
