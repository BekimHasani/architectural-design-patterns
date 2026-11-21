package com.designpatterns.level03.integration.provider.stripe;

import com.designpatterns.level03.integration.gateway.ChargeRequest;
import com.designpatterns.level03.integration.gateway.GatewayResult;
import com.designpatterns.level03.integration.gateway.GatewayResult.FailureKind;
import com.designpatterns.level03.integration.gateway.RefundRequest;
import com.designpatterns.level03.model.Money;
import com.designpatterns.simulators.Fault;
import com.designpatterns.simulators.StripeLikeSimulator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** The adapter against the Stripe-like simulator: real HTTP, real JSON, injected faults. */
class StripeGatewayTest {

    private static StripeLikeSimulator sim;
    private static StripeGateway gateway;

    @BeforeAll
    static void start() throws IOException {
        sim = StripeLikeSimulator.start(0);
        gateway = new StripeGateway(sim.baseUrl(), "sk_test_level03");
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
    void aChargeSucceedsAndMovesMoney() {
        GatewayResult result = gateway.charge(tenEuro());

        assertThat(result).isInstanceOf(GatewayResult.Succeeded.class);
        assertThat(sim.ledger()).singleElement().satisfies(entry -> {
            assertThat(entry.amountMinor()).isEqualTo(1_000);
            assertThat(entry.currency()).isEqualTo("EUR");
        });
    }

    @Test
    void aDeclineIsADecline() {
        sim.injectFault(Fault.DECLINE, 1);

        assertThat(gateway.charge(tenEuro())).isInstanceOf(GatewayResult.Declined.class);
        assertThat(sim.ledger()).isEmpty();
    }

    @Test
    void anUnavailableProviderChargedNothing() {
        sim.injectFault(Fault.HTTP_503, 1);

        assertThat(gateway.charge(tenEuro())).isInstanceOfSatisfying(GatewayResult.Failed.class,
                failed -> assertThat(failed.kind()).isEqualTo(FailureKind.NOTHING_CHARGED));
    }

    @Test
    void aLostResponseMeansTheOutcomeIsUnknown() {
        sim.injectFault(Fault.DROP_AFTER_COMMIT, 1);

        assertThat(gateway.charge(tenEuro())).isInstanceOfSatisfying(GatewayResult.Failed.class,
                failed -> assertThat(failed.kind()).isEqualTo(FailureKind.OUTCOME_UNKNOWN));
        assertThat(sim.ledger()).as("the simulator DID charge").hasSize(1);
    }

    @Test
    void aPartialRefundIsRecorded() {
        GatewayResult.Succeeded charged = (GatewayResult.Succeeded) gateway.charge(tenEuro());

        GatewayResult refund = gateway.refund(new RefundRequest("ref_1", charged.providerReference(), Money.of(400, "EUR")));

        assertThat(refund).isInstanceOf(GatewayResult.Succeeded.class);
        assertThat(sim.refunds()).singleElement().satisfies(entry -> assertThat(entry.amountMinor()).isEqualTo(400));
    }
}
