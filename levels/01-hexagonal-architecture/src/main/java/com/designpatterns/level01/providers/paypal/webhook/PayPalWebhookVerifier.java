package com.designpatterns.level01.providers.paypal.webhook;

import com.designpatterns.level01.providers.support.Signatures;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;

/**
 * {@code Sim-Transmission-Sig = hex hmac_sha256(secret, transmissionId + "|" + transmissionTime + "|" + body)}.
 * (Real PayPal verifies with a certificate or an API call; same idea: the payload alone proves nothing.)
 */
final class PayPalWebhookVerifier {

    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final String secret;
    private final Clock clock;

    PayPalWebhookVerifier(String secret, Clock clock) {
        this.secret = secret;
        this.clock = clock;
    }

    boolean verify(String transmissionId, String transmissionTime, String signature, String body) {
        if (transmissionId == null || transmissionTime == null || signature == null) {
            return false;
        }
        try {
            Duration age = Duration.between(Instant.parse(transmissionTime), clock.instant()).abs();
            if (age.compareTo(TOLERANCE) > 0) {
                return false;
            }
            byte[] expected = Signatures.hmacSha256(secret, transmissionId + "|" + transmissionTime + "|" + body);
            return Signatures.matches(expected, HexFormat.of().parseHex(signature));
        } catch (DateTimeParseException | IllegalArgumentException malformed) {
            return false;
        }
    }
}
