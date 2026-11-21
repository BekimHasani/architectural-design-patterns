package com.designpatterns.level08.payments.application.ports;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The classification table from the README, as an executable spec. */
class FailureCategoryTest {

    @ParameterizedTest(name = "{0}: retry={1} fallback={2} unknown={3}")
    @CsvSource({
            "INVALID_REQUEST,      false, false, false",
            "RATE_LIMITED,         true,  true,  false",
            "PROVIDER_UNAVAILABLE, true,  true,  false",
            "OUTCOME_UNKNOWN,      false, false, true",
            "NOT_PROCESSED,        false, false, false"})
    void classification(FailureCategory category, boolean retry, boolean fallback, boolean unknown) {
        assertThat(category.retryable()).isEqualTo(retry);
        assertThat(category.safeToFallback()).isEqualTo(fallback);
        assertThat(category.outcomeUnknown()).isEqualTo(unknown);
    }
}
