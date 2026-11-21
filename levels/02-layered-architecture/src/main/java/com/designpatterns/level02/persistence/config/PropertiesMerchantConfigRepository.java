package com.designpatterns.level02.persistence.config;

import com.designpatterns.level02.model.MerchantId;
import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.persistence.MerchantConfigRepository;

import java.util.Map;
import java.util.Optional;

/**
 * Merchants read from configuration ({@code payment.merchants}). In production this would be a
 * secret store; only this class would change, because everything else sees the interface.
 */
public final class PropertiesMerchantConfigRepository implements MerchantConfigRepository {

    /** merchant → provider → credential name → value */
    private final Map<String, Map<String, Map<String, String>>> merchants;

    public PropertiesMerchantConfigRepository(Map<String, Map<String, Map<String, String>>> merchants) {
        this.merchants = Map.copyOf(merchants);
    }

    @Override
    public boolean exists(MerchantId merchantId) {
        return merchants.containsKey(merchantId.value());
    }

    @Override
    public Optional<Map<String, String>> credentials(MerchantId merchantId, ProviderId providerId) {
        return Optional.ofNullable(merchants.get(merchantId.value()))
                .map(providers -> providers.get(providerId.value()))
                .map(Map::copyOf);
    }
}
