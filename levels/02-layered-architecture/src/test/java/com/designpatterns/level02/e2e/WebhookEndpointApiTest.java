package com.designpatterns.level02.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ONE webhook endpoint ({@code POST /webhooks/{provider}}) at its edges. The happy paths, with
 * real signed webhooks from the simulators, are covered in {@link PlatformApiTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:webhooks;DB_CLOSE_DELAY=-1",
        "payment.reconciliation.enabled=false"})
class WebhookEndpointApiTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcClient jdbc;

    @Test
    void aProviderWithoutWebhooksIsNotFound() {
        assertThat(post("/webhooks/chargeonly", new HttpHeaders(), "{}").getStatusCode().value()).isEqualTo(404);
        assertThat(post("/webhooks/nobody", new HttpHeaders(), "{}").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void aForgedStripeWebhookIs401_andNothingIsStored() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Sim-Signature", "t=1,v1=00");

        ResponseEntity<String> response = post("/webhooks/stripe", headers, "{\"id\":\"evt_forged\",\"type\":\"x\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM provider_event_inbox WHERE event_id = 'evt_forged'")
                .query(Long.class).single()).isZero();
    }

    @Test
    void anUnsignedAdyenBatchIs401() {
        String batch = """
                {"notificationItems":[{"NotificationRequestItem":{"eventCode":"AUTHORISATION","success":"true",
                 "pspReference":"p","merchantReference":"att_x","additionalData":{}}}]}""";

        assertThat(post("/webhooks/adyen", new HttpHeaders(), batch).getStatusCode().value()).isEqualTo(401);
    }

    private ResponseEntity<String> post(String path, HttpHeaders headers, String body) {
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        return http.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }
}
