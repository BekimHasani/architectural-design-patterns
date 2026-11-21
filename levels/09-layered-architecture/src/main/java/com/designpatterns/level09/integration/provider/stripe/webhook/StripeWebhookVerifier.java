package com.designpatterns.level09.integration.provider.stripe.webhook;

import com.designpatterns.level09.integration.support.Signatures;


import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

/**
 * {@code Sim-Signature: t=<unix seconds>,v1=<hex hmac_sha256(secret, t + "." + body)>}.
 * The timestamp is signed too, and must be recent: an attacker who captured an old, valid
 * webhook can't replay it later.
 */
final class StripeWebhookVerifier {

    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final String secret;
    private final Clock clock;

    StripeWebhookVerifier(String secret, Clock clock) {
        this.secret = secret;
        this.clock = clock;
    }

    boolean verify(String header, String body) {
        if (header == null) {
            return false;
        }
        String t = null;
        String v1 = null;
        for (String part : header.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals("t")) {
                t = kv[1];
            } else if (kv.length == 2 && kv[0].equals("v1")) {
                v1 = kv[1];
            }
        }
        if (t == null || v1 == null) {
            return false;
        }
        try {
            long ageSeconds = Math.abs(clock.instant().getEpochSecond() - Long.parseLong(t));
            if (ageSeconds > TOLERANCE.toSeconds()) {
                return false;
            }
            return Signatures.matches(Signatures.hmacSha256(secret, t + "." + body), HexFormat.of().parseHex(v1));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }
}
