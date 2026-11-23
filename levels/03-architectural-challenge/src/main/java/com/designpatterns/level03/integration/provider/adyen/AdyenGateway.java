package com.designpatterns.level03.integration.provider.adyen;

import com.designpatterns.level03.integration.gateway.ChargeRequest;
import com.designpatterns.level03.integration.gateway.GatewayResult;
import com.designpatterns.level03.integration.gateway.PaymentGateway;
import com.designpatterns.level03.integration.gateway.RefundRequest;
import com.designpatterns.level03.integration.support.ProviderHttp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import static com.designpatterns.level03.integration.support.ProviderHttp.exchange;
import static com.designpatterns.level03.integration.support.ProviderHttp.guard;

/** ADAPTER for the Adyen-like API. Adyen has no refund reason field, so {@link RefundRequest#reason()} is not sent. */
@Component
public class AdyenGateway implements PaymentGateway {

    private final RestClient adyen;
    private final String apiKey;
    private final String merchantAccount;

    public AdyenGateway(@Value("${payment.adyen.base-url}") String baseUrl,
                        @Value("${payment.adyen.api-key}") String apiKey,
                        @Value("${payment.adyen.merchant-account}") String merchantAccount) {
        this.adyen = ProviderHttp.restClient(baseUrl);
        this.apiKey = apiKey;
        this.merchantAccount = merchantAccount;
    }

    @Override
    public String id() {
        return "adyen";
    }

    @Override
    public GatewayResult charge(ChargeRequest request) {
        return guard(() -> AdyenMapper.toChargeResult(exchange(adyen.post()
                .uri("/v71/payments")
                .header("X-API-Key", apiKey)
                .header("Idempotency-Key", request.attemptId())
                .body(AdyenMapper.toRequest(request, merchantAccount)))));
    }

    @Override
    public GatewayResult refund(RefundRequest request) {
        return guard(() -> AdyenMapper.toRefundResult(exchange(adyen.post()
                .uri("/v71/payments/{psp}/refunds", request.providerReference())
                .header("X-API-Key", apiKey)
                .header("Idempotency-Key", request.operationId())
                .body(AdyenMapper.toRefund(request, merchantAccount)))));
    }
}
