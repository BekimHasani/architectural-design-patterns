package com.designpatterns.level03.service.routing;

import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingServiceTest {

    private final RoutingService routing = new RoutingService(List.of("stripe", "adyen"), Map.of("EUR", "adyen"));

    @Test
    void thePreferredProviderForTheCurrencyComesFirst() {
        assertThat(routing.candidatesFor(Currency.getInstance("EUR"))).containsExactly("adyen", "stripe");
    }

    @Test
    void otherCurrenciesUseTheDefaultOrder() {
        assertThat(routing.candidatesFor(Currency.getInstance("USD"))).containsExactly("stripe", "adyen");
    }

    @Test
    void aPreferredProviderOutsideTheDefaultOrderIsStillTried() {
        RoutingService withExtra = new RoutingService(List.of("stripe"), Map.of("GBP", "paypal"));

        assertThat(withExtra.candidatesFor(Currency.getInstance("GBP"))).containsExactly("paypal", "stripe");
    }
}
