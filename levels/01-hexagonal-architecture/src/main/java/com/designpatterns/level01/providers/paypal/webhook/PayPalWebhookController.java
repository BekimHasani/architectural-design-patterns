package com.designpatterns.level01.providers.paypal.webhook;

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

/** INBOUND ADAPTER: PayPal → us. Same shape as Stripe's, different format. */
@RestController
class PayPalWebhookController {

    private final RecordProviderPaymentEventHandler handler;
    private final PayPalWebhookVerifier verifier;

    PayPalWebhookController(RecordProviderPaymentEventHandler handler,
                            @Value("${payment.paypal.webhook-secret}") String secret, Clock clock) {
        this.handler = handler;
        this.verifier = new PayPalWebhookVerifier(secret, clock);
    }

    @PostMapping("/webhooks/paypal")
    ResponseEntity<Void> receive(@RequestHeader(name = "Sim-Transmission-Id", required = false) String id,
                                 @RequestHeader(name = "Sim-Transmission-Time", required = false) String time,
                                 @RequestHeader(name = "Sim-Transmission-Sig", required = false) String signature,
                                 @RequestBody String body) {
        if (!verifier.verify(id, time, signature, body)) {
            return ResponseEntity.status(401).build();
        }
        Optional<RecordProviderPaymentEvent> event = PayPalWebhookMapper.toCommand(body);
        if (event.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        handler.handle(event.get(), body);
        return ResponseEntity.ok().build();
    }
}
