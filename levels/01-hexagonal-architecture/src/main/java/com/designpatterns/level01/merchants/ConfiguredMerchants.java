package com.designpatterns.level01.merchants;

import com.designpatterns.level01.payments.application.ports.MerchantCredentials;
import com.designpatterns.level01.payments.application.ports.ProviderCredentials;
import com.designpatterns.level01.payments.domain.MerchantId;
import com.designpatterns.level01.payments.domain.ProviderId;

import java.util.Map;
import java.util.Optional;

/**
 * THE MERCHANTS MODULE: who the tenants are and which contracts they hold. Credentials come from
 * configuration here; in production this class would read a secret store. Nothing else changes,
 * because the rest of the system only sees the {@link MerchantCredentials} port.
 */
public final class ConfiguredMerchants implements MerchantCredentials {

    /** merchant → provider → credential name → value */
    private final Map<String, Map<String, Map<String, String>>> merchants;

    public ConfiguredMerchants(Map<String, Map<String, Map<String, String>>> merchants) {
        this.merchants = Map.copyOf(merchants);
    }

    @Override
    public boolean knows(MerchantId merchantId) {
        return merchants.containsKey(merchantId.value());
    }

    @Override
    public Optional<ProviderCredentials> find(MerchantId merchantId, ProviderId providerId) {
        return Optional.ofNullable(merchants.get(merchantId.value()))
                .map(providers -> providers.get(providerId.value()))
                .map(ProviderCredentials::new);
    }
}
