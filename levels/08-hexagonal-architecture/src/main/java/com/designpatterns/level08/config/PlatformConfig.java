package com.designpatterns.level08.config;

import com.designpatterns.level08.audit.AuditTrail;
import com.designpatterns.level08.merchants.ConfiguredMerchants;
import com.designpatterns.level08.payments.application.commands.AuthorizePayment;
import com.designpatterns.level08.payments.application.commands.PaymentOperations;
import com.designpatterns.level08.payments.application.events.RecordProviderPaymentEventHandler;
import com.designpatterns.level08.payments.application.orchestration.PaymentOrchestrator;
import com.designpatterns.level08.payments.application.capabilities.ProviderAdapter;
import com.designpatterns.level08.payments.application.ports.CallPolicy;
import com.designpatterns.level08.payments.application.ports.GatewayRegistry;
import com.designpatterns.level08.payments.application.ports.IdempotencyStore;
import com.designpatterns.level08.payments.application.ports.MerchantCredentials;
import com.designpatterns.level08.payments.application.ports.PaymentAttemptLog;
import com.designpatterns.level08.payments.application.ports.PaymentRepository;
import com.designpatterns.level08.payments.application.ports.ProviderEventInbox;
import com.designpatterns.level08.payments.application.reconciliation.ReconcileUnknownPayments;
import com.designpatterns.level08.payments.application.routing.ProviderRouter;
import com.designpatterns.level08.payments.domain.ProviderId;
import com.designpatterns.level08.platform.persistence.JdbcIdempotencyStore;
import com.designpatterns.level08.platform.persistence.JdbcPaymentAttemptLog;
import com.designpatterns.level08.platform.persistence.JdbcPaymentRepository;
import com.designpatterns.level08.platform.persistence.JdbcProviderEventInbox;
import com.designpatterns.level08.platform.resilience.CircuitBreakerPolicy;
import com.designpatterns.level08.platform.resilience.RetryPolicy;
import com.designpatterns.level08.providers.InMemoryGatewayRegistry;
import com.designpatterns.level08.providers.support.ProviderFactory;
import com.designpatterns.level08.providers.support.ProviderHttp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** COMPOSITION ROOT: the only class that knows every module. */
@Configuration
@EnableScheduling
class PlatformConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    // ---- payments module ----------------------------------------------------------------------

    @Bean
    AuthorizePayment authorizePayment(ProviderRouter router, PaymentOrchestrator orchestrator, PaymentRepository payments,
                                      PaymentAttemptLog attempts, MerchantCredentials credentials,
                                      PaymentOperations operations) {
        return new AuthorizePayment(router, orchestrator, payments, attempts, credentials, operations);
    }

    @Bean
    PaymentOperations paymentOperations(PaymentRepository payments, PaymentAttemptLog attempts, GatewayRegistry gateways,
                                        MerchantCredentials credentials) {
        return new PaymentOperations(payments, attempts, gateways, credentials);
    }

    @Bean
    PaymentOrchestrator paymentOrchestrator(GatewayRegistry gateways, PaymentRepository payments,
                                            PaymentAttemptLog attempts, MerchantCredentials credentials, Clock clock) {
        return new PaymentOrchestrator(gateways, payments, attempts, credentials, clock);
    }

    @Bean
    ReconcileUnknownPayments reconcileUnknownPayments(PaymentRepository payments, GatewayRegistry gateways,
                                                      MerchantCredentials credentials, PaymentOperations operations) {
        return new ReconcileUnknownPayments(payments, gateways, credentials, operations);
    }

    @Bean
    RecordProviderPaymentEventHandler recordProviderPaymentEventHandler(ProviderEventInbox inbox, PaymentAttemptLog attempts,
                                                                        PaymentRepository payments) {
        return new RecordProviderPaymentEventHandler(inbox, attempts, payments);
    }

    // ---- merchants and audit modules ----------------------------------------------------------

    @Bean
    MerchantCredentials merchantCredentials(PaymentProperties p) {
        return new ConfiguredMerchants(p.merchants());
    }

    @Bean
    AuditTrail auditTrail(JdbcClient jdbc) {
        return new AuditTrail(jdbc);
    }

    // ---- platform: storage --------------------------------------------------------------------

    @Bean
    PaymentRepository paymentRepository(JdbcClient jdbc, PlatformTransactionManager transactions, Clock clock) {
        return new JdbcPaymentRepository(jdbc, new TransactionTemplate(transactions), clock);
    }

    @Bean
    PaymentAttemptLog paymentAttemptLog(JdbcClient jdbc) {
        return new JdbcPaymentAttemptLog(jdbc);
    }

    @Bean
    ProviderEventInbox providerEventInbox(JdbcClient jdbc, Clock clock) {
        return new JdbcProviderEventInbox(jdbc, clock);
    }

    @Bean
    IdempotencyStore idempotencyStore(JdbcClient jdbc, Clock clock) {
        return new JdbcIdempotencyStore(jdbc, clock);
    }

    // ---- providers: plug-ins ------------------------------------------------------------------

    /**
     * Every enabled provider, built ONCE. Providers register themselves (a {@link ProviderFactory} in
     * their folder), so this class names none of them. Every call runs through
     * breaker( retry( call ) ), whatever capability it uses.
     */
    @Bean
    GatewayRegistry gatewayRegistry(PaymentProperties p, List<ProviderFactory> factories, RestClient.Builder restClient,
                                    Clock clock) {
        PaymentProperties.Resilience r = p.resilience();
        Map<String, ProviderFactory> byName = factories.stream().collect(Collectors.toMap(ProviderFactory::name, f -> f));
        Map<ProviderId, ProviderAdapter> adapters = new HashMap<>();
        Map<ProviderId, CallPolicy> policies = new HashMap<>();
        for (String name : p.enabled()) {
            ProviderFactory factory = byName.get(name);
            if (factory == null) {
                throw new IllegalStateException("Unknown provider in payment.enabled: " + name);
            }
            RestClient.Builder http = ProviderHttp.withTimeouts(restClient.clone(), r.connectTimeout(), r.readTimeout());
            adapters.put(ProviderId.of(name), factory.create(http, p.baseUrls().get(name)));
            CallPolicy retry = new RetryPolicy(r.maxAttempts(), r.retryBaseDelay());
            CallPolicy breaker = new CircuitBreakerPolicy(r.breakerFailureThreshold(), r.breakerOpenFor(), clock);
            policies.put(ProviderId.of(name), call -> breaker.run(() -> retry.run(call)));
        }
        return new InMemoryGatewayRegistry(adapters, policies);
    }
}
