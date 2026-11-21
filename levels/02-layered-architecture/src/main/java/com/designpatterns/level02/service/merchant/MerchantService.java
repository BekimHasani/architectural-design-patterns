package com.designpatterns.level02.service.merchant;

import com.designpatterns.level02.integration.gateway.ProviderCredentials;
import com.designpatterns.level02.model.MerchantId;
import com.designpatterns.level02.model.ProviderId;
import com.designpatterns.level02.persistence.MerchantConfigRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Business questions about merchants: "do we know this merchant?" and "may it use this provider, with
 * which secrets?".
 *
 * <p>It is also the BRIDGE between two layers that must not meet: it reads raw credentials from
 * persistence and hands them to integration as {@link ProviderCredentials}. In Level 1, one class
 * (ConfiguredMerchants) implemented a core port. Layered rules split it into storage + business logic.
 */
@Service
public class MerchantService {

    private final MerchantConfigRepository merchants;

    public MerchantService(MerchantConfigRepository merchants) {
        this.merchants = merchants;
    }

    public boolean knows(MerchantId merchantId) {
        return merchants.exists(merchantId);
    }

    /**
     * FLOW A.7 (and before every provider call): empty if this merchant has no contract with this
     * provider. Raw strings from persistence become integration's {@code ProviderCredentials} here.
     */
    public Optional<ProviderCredentials> credentials(MerchantId merchantId, ProviderId providerId) {
        return merchants.credentials(merchantId, providerId).map(ProviderCredentials::new);
    }
}
