package com.designpatterns.level08.providers;

import com.designpatterns.level08.providers.support.ProviderHttp.Reply;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads recorded provider responses from {@code src/test/resources/fixtures}. */
public final class Fixtures {

    private Fixtures() {
    }

    public static Reply reply(int status, String fixture) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + fixture)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture " + fixture);
            }
            return new Reply(status, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
