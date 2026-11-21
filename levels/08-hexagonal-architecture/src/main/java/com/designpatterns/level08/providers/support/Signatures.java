package com.designpatterns.level08.providers.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/** HMAC helpers shared by the webhook verifiers. */
public final class Signatures {

    private Signatures() {
    }

    public static byte[] hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Constant-time comparison. {@code String.equals} stops at the first different character,
     * which lets an attacker guess a signature byte by byte by timing the answers.
     */
    public static boolean matches(byte[] expected, byte[] actual) {
        return actual != null && MessageDigest.isEqual(expected, actual);
    }
}
