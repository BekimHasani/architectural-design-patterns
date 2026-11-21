package com.designpatterns.level01.providers.stripe.webhook;

import com.designpatterns.level01.payments.application.events.RecordProviderPaymentEvent;
import com.designpatterns.level01.payments.application.events.RecordProviderPaymentEventHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Optional;

/**
 * INBOUND ADAPTER: Stripe → us. verify → map → the shared command → 200.
 * It lives next to the outbound Stripe adapter because both speak "Stripe"; the rest of the
 * app only ever sees {@link RecordProviderPaymentEvent}.
 */
@RestController
class StripeWebhookController {

    private final RecordProviderPaymentEventHandler handler;
    private final StripeWebhookVerifier verifier;

    StripeWebhookController(RecordProviderPaymentEventHandler handler,
                            @Value("${payment.stripe.webhook-secret}") String secret, Clock clock) {
        this.handler = handler;
        this.verifier = new StripeWebhookVerifier(secret, clock);
    }

    @PostMapping("/webhooks/stripe")
    ResponseEntity<Void> receive(@RequestHeader(name = "Sim-Signature", required = false) String signature,
                                 @RequestBody String body) {
        if (!verifier.verify(signature, body)) {
            return ResponseEntity.status(401).build(); // forged or tampered: nothing is stored
        }
        Optional<RecordProviderPaymentEvent> event = StripeWebhookMapper.toCommand(body);
        if (event.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        handler.handle(event.get(), body);
        return ResponseEntity.ok().build(); // also for duplicates: "got it, stop retrying"
    }
}
