package com.designpatterns.level02.e2e;

import com.designpatterns.simulators.AdyenLikeSimulator;
import com.designpatterns.simulators.BaseSimulator;
import com.designpatterns.simulators.PayPalLikeSimulator;
import com.designpatterns.simulators.StripeLikeSimulator;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

/** All simulators on random ports, wired into the app's config. */
final class Simulators {

    final StripeLikeSimulator stripe;
    final PayPalLikeSimulator paypal;
    final AdyenLikeSimulator adyen;

    Simulators() {
        try {
            stripe = StripeLikeSimulator.start(0);
            paypal = PayPalLikeSimulator.start(0);
            adyen = AdyenLikeSimulator.start(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void register(DynamicPropertyRegistry registry) {
        registry.add("payment.base-urls.stripe", stripe::baseUrl);
        registry.add("payment.base-urls.paypal", paypal::baseUrl);
        registry.add("payment.base-urls.adyen", adyen::baseUrl);
        registry.add("payment.resilience.read-timeout", () -> "1s");
        registry.add("payment.resilience.retry-base-delay", () -> "1ms");
        registry.add("payment.resilience.breaker-failure-threshold", () -> "1000");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        registry.add("payment.reconciliation.enabled", () -> "false");
    }

    List<BaseSimulator> all() {
        return List.of(stripe, paypal, adyen);
    }
}
