package com.designpatterns.level02.persistence;

import com.designpatterns.level02.model.MerchantId;
import com.designpatterns.level02.model.ProviderId;

import java.util.Map;
import java.util.Optional;

/**
 * Where merchants and their provider credentials are kept. Returns RAW values: turning them into the
 * integration layer's {@code ProviderCredentials} is the service layer's job, because persistence
 * and integration must not know each other.
 */
public interface MerchantConfigRepository {

    boolean exists(MerchantId merchantId);

    /** Credential name → value, or empty if this merchant has no contract with this provider. */
    Optional<Map<String, String>> credentials(MerchantId merchantId, ProviderId providerId);
}
