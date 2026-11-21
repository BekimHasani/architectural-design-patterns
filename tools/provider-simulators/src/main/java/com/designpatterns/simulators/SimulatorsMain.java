package com.designpatterns.simulators;

/**
 * Runs all simulators on their default ports until the process is killed. Webhooks go to
 * {@code $APP_URL/webhooks/{stripe|paypal|adyen}} (default http://localhost:8080), signed with
 * the secrets below, which match the Level 6+ application.yml.
 */
public final class SimulatorsMain {

    public static final String STRIPE_WEBHOOK_SECRET = "whsec_sim";
    public static final String PAYPAL_WEBHOOK_SECRET = "paypal_whsec_sim";
    public static final String ADYEN_HMAC_KEY = "adyen_hmac_sim";

    private SimulatorsMain() {
    }

    public static void main(String[] args) throws Exception {
        String app = System.getenv().getOrDefault("APP_URL", "http://localhost:8080");

        StripeLikeSimulator stripe = StripeLikeSimulator.start(StripeLikeSimulator.DEFAULT_PORT);
        stripe.sendWebhooksTo(app + "/webhooks/stripe", STRIPE_WEBHOOK_SECRET);
        PayPalLikeSimulator paypal = PayPalLikeSimulator.start(PayPalLikeSimulator.DEFAULT_PORT);
        paypal.sendWebhooksTo(app + "/webhooks/paypal", PAYPAL_WEBHOOK_SECRET);
        AdyenLikeSimulator adyen = AdyenLikeSimulator.start(AdyenLikeSimulator.DEFAULT_PORT);
        adyen.sendWebhooksTo(app + "/webhooks/adyen", ADYEN_HMAC_KEY);

        ProviderXSimulator providerX = ProviderXSimulator.start(ProviderXSimulator.DEFAULT_PORT);
        SyntheticSimulator.start(4105, SyntheticSimulator.Mode.HOLD);
        SyntheticSimulator.start(4106, SyntheticSimulator.Mode.CHARGE);
        SyntheticSimulator.start(4107, SyntheticSimulator.Mode.ASYNC);

        System.out.println("Provider X simulator:  " + providerX.baseUrl());
        System.out.println("Stripe-like simulator: " + stripe.baseUrl());
        System.out.println("PayPal-like simulator: " + paypal.baseUrl()
                + "  (client " + PayPalLikeSimulator.CLIENT_ID + " / " + PayPalLikeSimulator.CLIENT_SECRET + ")");
        System.out.println("Adyen-like simulator:  " + adyen.baseUrl());
        System.out.println("Webhooks → " + app + "/webhooks/{stripe|paypal|adyen} (earlier levels just answer 404)");
        Thread.currentThread().join();
    }
}
