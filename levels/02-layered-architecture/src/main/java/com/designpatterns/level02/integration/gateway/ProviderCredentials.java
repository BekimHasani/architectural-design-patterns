package com.designpatterns.level02.integration.gateway;

import java.util.Map;

/**
 * One merchant's secrets for one provider. Opaque to the services: only the matching adapter knows
 * which keys it needs. {@code toString} hides the values, so they can't end up in a log.
 */
public record ProviderCredentials(Map<String, String> values) {

    public ProviderCredentials {
        values = Map.copyOf(values);
    }

    public static ProviderCredentials of(String key, String value) {
        return new ProviderCredentials(Map.of(key, value));
    }

    public String require(String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing credential '" + key + "'");
        }
        return value;
    }

    @Override
    public String toString() {
        return "ProviderCredentials" + values.keySet() + "(hidden)";
    }
}
