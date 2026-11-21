package com.designpatterns.level08.payments.application.ports;

import com.designpatterns.level08.payments.domain.MerchantId;
import com.designpatterns.level08.payments.domain.ProviderId;

import java.util.Optional;

/** Port, implemented by the merchants module. In production: a secret store, read per request. */
public interface MerchantCredentials {

    boolean knows(MerchantId merchantId);

    /** Empty if this merchant has no contract with this provider. */
    Optional<ProviderCredentials> find(MerchantId merchantId, ProviderId providerId);
}
