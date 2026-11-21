package com.designpatterns.level01.api;

import com.designpatterns.level01.payments.application.reconciliation.ReconcileUnknownPayments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Two ways to start the same use case: an operator, or a timer. */
final class ReconciliationEntryPoints {

    private ReconciliationEntryPoints() {
    }

    @RestController
    static class Admin {

        private final ReconcileUnknownPayments reconcile;

        Admin(ReconcileUnknownPayments reconcile) {
            this.reconcile = reconcile;
        }

        @PostMapping("/admin/reconcile")
        ReconcileUnknownPayments.Report run() {
            return reconcile.run();
        }
    }

    @Component("reconciliationJob")
    @ConditionalOnProperty(name = "payment.reconciliation.enabled", havingValue = "true")
    static class Job {

        private final ReconcileUnknownPayments reconcile;

        Job(ReconcileUnknownPayments reconcile) {
            this.reconcile = reconcile;
        }

        @Scheduled(fixedDelayString = "${payment.reconciliation.interval}", initialDelayString = "${payment.reconciliation.interval}")
        void run() {
            reconcile.run();
        }
    }
}
