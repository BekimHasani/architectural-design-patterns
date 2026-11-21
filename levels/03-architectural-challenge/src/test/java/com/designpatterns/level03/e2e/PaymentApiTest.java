package com.designpatterns.level03.e2e;

import com.designpatterns.simulators.AdyenLikeSimulator;
import com.designpatterns.simulators.Fault;
import com.designpatterns.simulators.StripeLikeSimulator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP in → the whole application → simulators. Checks what the customer sees AND what really moved. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentApiTest {

    private static final StripeLikeSimulator STRIPE;
    private static final AdyenLikeSimulator ADYEN;

    static {
        try {
            STRIPE = StripeLikeSimulator.start(0);
            ADYEN = AdyenLikeSimulator.start(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void simulators(DynamicPropertyRegistry registry) {
        registry.add("payment.stripe.base-url", STRIPE::baseUrl);
        registry.add("payment.adyen.base-url", ADYEN::baseUrl);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    }

    @AfterAll
    static void stopSimulators() {
        STRIPE.close();
        ADYEN.close();
    }

    @Autowired
    TestRestTemplate http;

    /** The JSON of a payment, as a client sees it. */
    record PaymentJson(String id, String merchantId, String customerId, long amountMinor, String currency, String status,
                       String providerId, String providerReference, long refundedMinor, String failureReason) {
    }

    @BeforeEach
    void resetSimulators() {
        STRIPE.reset();
        ADYEN.reset();
    }

    private <T> ResponseEntity<T> post(String path, String merchant, String idempotencyKey, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Merchant-Id", merchant);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), type);
    }

    private ResponseEntity<PaymentJson> pay(String merchant, String key, String currency, long amountMinor) {
        return post("/payments", merchant, key, Map.of("customerId", "cus_1", "currency", currency, "amountMinor", amountMinor),
                PaymentJson.class);
    }

    private ResponseEntity<PaymentJson> pay(String currency, long amountMinor) {
        return pay("acme", UUID.randomUUID().toString(), currency, amountMinor);
    }

    private ResponseEntity<String> get(String path, String merchant) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Merchant-Id", merchant);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void aUsdPaymentIsChargedAtStripe() {
        ResponseEntity<PaymentJson> response = pay("USD", 1_999);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo("CAPTURED");
        assertThat(response.getBody().providerId()).isEqualTo("stripe");
        assertThat(response.getHeaders().getLocation()).hasPath("/payments/" + response.getBody().id());
        assertThat(STRIPE.ledger()).singleElement().satisfies(entry -> assertThat(entry.amountMinor()).isEqualTo(1_999));
    }

    @Test
    void aEuroPaymentIsChargedAtAdyen() {
        ResponseEntity<PaymentJson> response = pay("EUR", 2_500);

        assertThat(response.getBody().status()).isEqualTo("CAPTURED");
        assertThat(response.getBody().providerId()).isEqualTo("adyen");
        assertThat(ADYEN.ledger()).hasSize(1);
        assertThat(STRIPE.ledger()).isEmpty();
    }

    @Test
    void aPaymentCanBeReadBackOnlyByItsMerchant() {
        String id = pay("USD", 1_000).getBody().id();

        assertThat(get("/payments/" + id, "acme").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/payments/" + id, "globex").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aDeclineIsFinalAndNoOtherProviderIsTried() {
        STRIPE.injectFault(Fault.DECLINE, 1);

        ResponseEntity<PaymentJson> response = pay("USD", 1_000);

        assertThat(response.getBody().status()).isEqualTo("DECLINED");
        assertThat(ADYEN.ledger()).isEmpty();
    }

    @Test
    void whenStripeIsDownAdyenChargesInstead() {
        STRIPE.injectFault(Fault.HTTP_503, 1);

        ResponseEntity<PaymentJson> response = pay("USD", 1_000);

        assertThat(response.getBody().status()).isEqualTo("CAPTURED");
        assertThat(response.getBody().providerId()).isEqualTo("adyen");
        assertThat(STRIPE.ledger()).isEmpty();
        assertThat(ADYEN.ledger()).hasSize(1);
    }

    @Test
    void aRetryWithTheSameKeyChargesOnlyOnce() {
        String key = UUID.randomUUID().toString();

        PaymentJson first = pay("acme", key, "USD", 1_000).getBody();
        PaymentJson retry = pay("acme", key, "USD", 1_000).getBody();

        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(STRIPE.ledger()).hasSize(1);
    }

    @Test
    void aPartialRefundIsSentToTheProviderThatCharged() {
        String id = pay("USD", 1_000).getBody().id();

        ResponseEntity<PaymentJson> refund = post("/payments/" + id + "/refunds", "acme", UUID.randomUUID().toString(),
                Map.of("amountMinor", 400, "reason", "damaged"), PaymentJson.class);

        assertThat(refund.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refund.getBody().status()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(refund.getBody().refundedMinor()).isEqualTo(400);
        assertThat(STRIPE.refunds()).singleElement().satisfies(entry -> assertThat(entry.amountMinor()).isEqualTo(400));
    }

    @Test
    void aRefundLargerThanThePaymentIsRefused() {
        String id = pay("EUR", 1_000).getBody().id();

        ResponseEntity<String> refund = post("/payments/" + id + "/refunds", "acme", UUID.randomUUID().toString(),
                Map.of("amountMinor", 1_001), String.class);

        assertThat(refund.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ADYEN.refunds()).isEmpty();
    }

    @Test
    void badRequestsAreRejectedBeforeAnyCharge() {
        assertThat(pay("acme", null, "USD", 1_000).getStatusCode()).as("no Idempotency-Key").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pay("acme", "k", "USD", 0).getStatusCode()).as("zero amount").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pay("acme", "k", "XQZ", 100).getStatusCode()).as("unknown currency").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(STRIPE.ledger()).isEmpty();
    }

    @Test
    void anUnknownPaymentIsNotFound() {
        assertThat(get("/payments/pay_nope", "acme").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Tag("challenge")
    void aLostResponseNeverChargesTheCustomerTwice() {
        STRIPE.injectFault(Fault.DROP_AFTER_COMMIT, 1);

        ResponseEntity<PaymentJson> response = pay("USD", 1_000);

        assertThat(STRIPE.ledger().size() + ADYEN.ledger().size()).as("charges across ALL providers").isEqualTo(1);
        assertThat(response.getBody().status()).isEqualTo("OUTCOME_UNKNOWN");
    }

    @Test
    @Tag("challenge")
    void twoMerchantsCanUseTheSameIdempotencyKey() {
        PaymentJson acme = pay("acme", "order-42", "USD", 1_000).getBody();

        ResponseEntity<PaymentJson> globex = pay("globex", "order-42", "USD", 2_000);

        assertThat(globex.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(globex.getBody().merchantId()).isEqualTo("globex");
        assertThat(globex.getBody().id()).isNotEqualTo(acme.id());
        assertThat(STRIPE.ledger()).hasSize(2);
    }
}
