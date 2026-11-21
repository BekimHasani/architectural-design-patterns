package com.designpatterns.level01.payments.application.routing;

import com.designpatterns.level01.payments.application.routing.rules.CurrencyRule;
import com.designpatterns.level01.payments.application.routing.rules.DefaultRule;
import com.designpatterns.level01.payments.application.routing.rules.MerchantOverrideRule;
import com.designpatterns.level01.payments.application.capabilities.Capability;
import com.designpatterns.level01.payments.application.capabilities.ProviderDescriptor;
import com.designpatterns.level01.payments.domain.CaptureMode;
import com.designpatterns.level01.payments.domain.ProviderId;
import com.designpatterns.level01.payments.domain.RefundTerms;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Routing tested with no adapters, no Spring, no network: only ids and rules. */
public class ProviderRouterTest {

    static final ProviderId STRIPE = ProviderId.of("stripe");
    static final ProviderId PAYPAL = ProviderId.of("paypal");
    static final ProviderId ADYEN = ProviderId.of("adyen");
    static final Currency EUR = Currency.getInstance("EUR");
    static final Currency USD = Currency.getInstance("USD");
    static final Currency GBP = Currency.getInstance("GBP");
    static final Currency JPY = Currency.getInstance("JPY");
    static final Currency CHF = Currency.getInstance("CHF");

    /** The same rules and data as application.yml. Reused by the checkout use-case tests. */
    public static final ProviderRouter ROUTER = new ProviderRouter(
            List.of(
                    new MerchantOverrideRule(Map.of("acme-enterprise", PAYPAL, "globex", ADYEN)),
                    new CurrencyRule(Map.of(EUR, ADYEN, USD, STRIPE, GBP, PAYPAL)),
                    new DefaultRule(STRIPE)),
            List.of(full(STRIPE, EUR, USD, GBP, JPY), full(PAYPAL, EUR, USD, GBP), full(ADYEN, EUR, USD, GBP, JPY)));

    @Test
    void currencyRules() {
        assertThat(first(EUR, null)).isEqualTo(ADYEN);
        assertThat(first(USD, null)).isEqualTo(STRIPE);
        assertThat(first(GBP, null)).isEqualTo(PAYPAL);
    }

    @Test
    void defaultWhenNoCurrencyRuleApplies() {
        assertThat(first(JPY, null)).isEqualTo(STRIPE);
    }

    @Test
    void merchantOverrideBeatsCurrency() {
        assertThat(first(EUR, "acme-enterprise")).isEqualTo(PAYPAL); // currency rule would say Adyen
    }

    @Test
    void unknownMerchantFallsThroughToCurrency() {
        assertThat(first(EUR, "small-shop")).isEqualTo(ADYEN);
    }

    @Test
    void conflictSkipsAProviderThatCannotTakeTheCurrency() {
        // acme-enterprise wants PayPal, but PayPal can't take JPY → next rule: none for JPY → default Stripe
        assertThat(ROUTER.candidates(context(JPY, "acme-enterprise"))).containsExactly(STRIPE);
    }

    @Test
    void candidatesAreOrderedByPrecedenceAndDeduplicated() {
        // merchant (PayPal) → currency EUR (Adyen) → default (Stripe). On failure, the fallback walks this list in order.
        assertThat(ROUTER.candidates(context(EUR, "acme-enterprise"))).containsExactly(PAYPAL, ADYEN, STRIPE);
        // currency USD (Stripe) and default (Stripe) collapse into one
        assertThat(ROUTER.candidates(context(USD, null))).containsExactly(STRIPE);
    }

    @Test
    void noCandidatesWhenNobodyTakesTheCurrency() {
        assertThat(ROUTER.candidates(context(CHF, null))).isEmpty();
    }

    @Test
    void anyNewRuleIsJustAnotherStrategy() {
        // e.g. "big payments go to Adyen": no change to the router or the checkout
        RoutingRule bigToAdyen = ctx -> Optional.of(ADYEN);
        ProviderRouter router = new ProviderRouter(List.of(bigToAdyen, new DefaultRule(STRIPE)),
                List.of(full(STRIPE, USD), full(ADYEN, USD)));

        assertThat(router.candidates(context(USD, null))).containsExactly(ADYEN, STRIPE);
    }

    /** The capability filter: the merchant's rule loses to what the payment needs. */
    @Test
    void aProviderThatCantDoWhatThePaymentNeedsIsNotACandidate() {
        ProviderId chargeOnly = ProviderId.of("chargeonly");
        ProviderId noRefunds = ProviderId.of("holdonly");
        ProviderRouter router = new ProviderRouter(
                List.of(new DefaultRule(chargeOnly), new DefaultRule(noRefunds), new DefaultRule(STRIPE)),
                List.of(descriptor(chargeOnly, Set.of(Capability.CHARGE), USD),
                        descriptor(noRefunds, Set.of(Capability.AUTHORIZE, Capability.CAPTURE, Capability.CANCEL), USD),
                        full(STRIPE, USD)));

        assertThat(router.candidates(new RoutingContext(USD, Optional.empty(), CaptureMode.AUTOMATIC, Set.of())))
                .containsExactly(chargeOnly, noRefunds, STRIPE);
        assertThat(router.candidates(new RoutingContext(USD, Optional.empty(), CaptureMode.MANUAL, Set.of())))
                .as("a charge-only provider can't hold funds").containsExactly(noRefunds, STRIPE);
        assertThat(router.candidates(new RoutingContext(USD, Optional.empty(), CaptureMode.MANUAL, Set.of(Capability.REFUND))))
                .containsExactly(STRIPE);
    }

    private static ProviderDescriptor full(ProviderId id, Currency... currencies) {
        return descriptor(id, Set.of(Capability.AUTHORIZE, Capability.CAPTURE, Capability.CANCEL, Capability.REFUND), currencies);
    }

    private static ProviderDescriptor descriptor(ProviderId id, Set<Capability> capabilities, Currency... currencies) {
        return new ProviderDescriptor(id, capabilities, Set.of(currencies), RefundTerms.ANY, ProviderDescriptor.Confirmation.SYNC);
    }

    private static ProviderId first(Currency currency, String merchantId) {
        return ROUTER.candidates(context(currency, merchantId)).get(0);
    }

    private static RoutingContext context(Currency currency, String merchantId) {
        return new RoutingContext(currency, Optional.ofNullable(merchantId));
    }
}
