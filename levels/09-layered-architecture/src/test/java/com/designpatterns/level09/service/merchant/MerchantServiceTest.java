package com.designpatterns.level09.service.merchant;

import com.designpatterns.level09.model.MerchantId;
import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.persistence.config.PropertiesMerchantConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantServiceTest {

    private final MerchantService merchants = new MerchantService(new PropertiesMerchantConfigRepository(Map.of(
            "acme", Map.of("stripe", Map.of("api-key", "sk_acme")))));

    @Test
    void knowsConfiguredMerchantsOnly() {
        assertThat(merchants.knows(new MerchantId("acme"))).isTrue();
        assertThat(merchants.knows(new MerchantId("evil"))).isFalse();
    }

    @Test
    void turnsRawConfigIntoProviderCredentials_onlyWhereAContractExists() {
        assertThat(merchants.credentials(new MerchantId("acme"), ProviderId.of("stripe")))
                .hasValueSatisfying(c -> assertThat(c.require("api-key")).isEqualTo("sk_acme"));
        assertThat(merchants.credentials(new MerchantId("acme"), ProviderId.of("adyen"))).isEmpty();
        assertThat(merchants.credentials(new MerchantId("evil"), ProviderId.of("stripe"))).isEmpty();
    }

    @Test
    void credentialsNeverShowUpInLogs() {
        assertThat(merchants.credentials(new MerchantId("acme"), ProviderId.of("stripe")).orElseThrow().toString())
                .doesNotContain("sk_acme");
    }
}
