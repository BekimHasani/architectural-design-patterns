package com.designpatterns.level03.integration.provider.adyen;

import com.designpatterns.level03.integration.gateway.ChargeRequest;
import com.designpatterns.level03.integration.gateway.GatewayResult;
import com.designpatterns.level03.integration.gateway.GatewayResult.FailureKind;
import com.designpatterns.level03.integration.gateway.RefundRequest;
import com.designpatterns.level03.model.Money;
import com.designpatterns.simulators.AdyenLikeSimulator;
import com.designpatterns.simulators.Fault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** The adapter against the Adyen-like simulator: real HTTP, real JSON, injected faults. */
class AdyenGatewayTest {

    private static AdyenLikeSimulator sim;
    private static AdyenGateway gateway;

    @BeforeAll
    static void start() throws IOException {
        sim = AdyenLikeSimulator.start(0);
        gateway = new AdyenGateway(sim.baseUrl(), "test_level03", "Level03ECOM");
    }

    @AfterAll
    static void stop() {
        sim.close();
    }

    @BeforeEach
    void reset() {
        sim.reset();
    }

    private static ChargeRequest tenEuro() {
        return new ChargeRequest("att_" + System.nanoTime(), Money.of(1_000, "EUR"));
    }

    @Test
    void aChargeIsAuthorisedAndCaptured() {
        assertThat(gateway.charge(tenEuro())).isInstanceOf(GatewayResult.Succeeded.class);
        assertThat(sim.ledger()).singleElement().satisfies(entry -> assertThat(entry.amountMinor()).isEqualTo(1_000));
    }

    @Test
    void aRefusalArrivesAsHttp200AndIsStillADecline() {
        sim.injectFault(Fault.DECLINE, 1);

        assertThat(gateway.charge(tenEuro())).isInstanceOf(GatewayResult.Declined.class);
        assertThat(sim.ledger()).isEmpty();
    }

    @Test
    void anUnavailableProviderChargedNothing() {
        sim.injectFault(Fault.HTTP_503, 1);

        GatewayResult result = gateway.charge(tenEuro());

        assertThat(result).isInstanceOfSatisfying(GatewayResult.Failed.class,
                failed -> assertThat(failed.kind()).isEqualTo(FailureKind.NOTHING_CHARGED));
    }

    @Test
    void aLostResponseMeansTheOutcomeIsUnknown() {
        sim.injectFault(Fault.DROP_AFTER_COMMIT, 1);

        GatewayResult result = gateway.charge(tenEuro());

        assertThat(result).isInstanceOfSatisfying(GatewayResult.Failed.class,
                failed -> assertThat(failed.kind()).isEqualTo(FailureKind.OUTCOME_UNKNOWN));
    }

    @Test
    void aPartialRefundIsRecorded() {
        GatewayResult.Succeeded charged = (GatewayResult.Succeeded) gateway.charge(tenEuro());

        GatewayResult refund = gateway.refund(new RefundRequest("ref_1", charged.providerReference(), Money.of(400, "EUR")));

        assertThat(refund).isInstanceOf(GatewayResult.Succeeded.class);
        assertThat(sim.refunds()).singleElement().satisfies(entry -> assertThat(entry.amountMinor()).isEqualTo(400));
    }
}
