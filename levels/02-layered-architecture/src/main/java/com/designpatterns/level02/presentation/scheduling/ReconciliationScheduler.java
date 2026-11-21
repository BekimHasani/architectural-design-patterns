package com.designpatterns.level02.presentation.scheduling;

import com.designpatterns.level02.service.reconciliation.ReconciliationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FLOW D.1: a timer is an ENTRY POINT, like an HTTP request, so it lives in the presentation layer and
 * only calls a service. Both entry points (this and {@code AdminReconciliationController}) run the same use case.
 *
 * <p>{@code @ConditionalOnProperty}: the bean exists only when {@code payment.reconciliation.enabled=true}
 * (tests switch it off to control when reconciliation runs). {@code @Scheduled(fixedDelayString)}: run
 * {@link #run} again N after the previous run FINISHED (never two runs at once); needs
 * {@code @EnableScheduling} in {@code config.ApplicationConfig}.
 */
@Component
@ConditionalOnProperty(name = "payment.reconciliation.enabled", havingValue = "true")
class ReconciliationScheduler {

    private final ReconciliationService reconciliation;

    ReconciliationScheduler(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.interval}",
            initialDelayString = "${payment.reconciliation.interval}")
    void run() {
        reconciliation.run();
    }
}
