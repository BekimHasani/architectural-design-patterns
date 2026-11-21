package com.designpatterns.level09.presentation.rest;

import com.designpatterns.level09.service.reconciliation.ReconciliationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** FLOW D.1: an operator starts reconciliation by hand. The timer does the same through {@code ReconciliationScheduler}. */
@RestController
class AdminReconciliationController {

    private final ReconciliationService reconciliation;

    AdminReconciliationController(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @PostMapping("/admin/reconcile")
    ReconciliationService.Report run() {
        return reconciliation.run();
    }
}
