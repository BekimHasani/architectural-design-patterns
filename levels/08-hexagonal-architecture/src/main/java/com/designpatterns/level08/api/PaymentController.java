package com.designpatterns.level08.api;

import com.designpatterns.level08.audit.AuditEntry;
import com.designpatterns.level08.audit.AuditTrail;
import com.designpatterns.level08.payments.application.commands.AuthorizePayment;
import com.designpatterns.level08.payments.application.commands.PaymentOperations;
import com.designpatterns.level08.payments.application.commands.PaymentResult;
import com.designpatterns.level08.payments.domain.MerchantId;
import com.designpatterns.level08.payments.domain.Money;
import com.designpatterns.level08.payments.domain.PaymentId;
import com.designpatterns.level08.payments.domain.PaymentOperation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The platform's public API. {@code X-Merchant-Id} says who is calling; real authentication would
 * sit in front of this. A payment of another merchant answers 404.
 */
@RestController
class PaymentController {

    private static final String MERCHANT = "X-Merchant-Id";
    private static final String KEY = "Idempotency-Key";

    private final AuthorizePayment authorizePayment;
    private final PaymentOperations operations;
    private final AuditTrail audit;
    private final IdempotentRequests idempotent;
    private final ObjectMapper json;

    PaymentController(AuthorizePayment authorizePayment, PaymentOperations operations, AuditTrail audit,
                      IdempotentRequests idempotent, ObjectMapper json) {
        this.authorizePayment = authorizePayment;
        this.operations = operations;
        this.audit = audit;
        this.idempotent = idempotent;
        this.json = json;
    }

    @PostMapping("/payments")
    ResponseEntity<String> authorize(@RequestHeader(MERCHANT) String merchant,
                                     @RequestHeader(name = KEY, required = false) String key,
                                     @RequestBody String body) {
        MerchantId merchantId = new MerchantId(merchant);
        return idempotent.execute(merchantId, "/payments", key, body, () -> {
            PaymentRequests.Authorize request = parse(body, PaymentRequests.Authorize.class);
            return lifecycle(authorizePayment.authorize(merchantId, request.customerId(),
                    Money.of(request.amountMinor(), request.currency()), request.captureMode(), request.alsoRequired()));
        });
    }

    @GetMapping("/payments/{id}")
    ResponseEntity<PaymentResponse> current(@RequestHeader(MERCHANT) String merchant, @PathVariable String id) {
        return lifecycle(authorizePayment.current(new MerchantId(merchant), new PaymentId(id)));
    }

    @PostMapping("/payments/{id}/complete")
    ResponseEntity<PaymentResponse> complete(@RequestHeader(MERCHANT) String merchant, @PathVariable String id) {
        return lifecycle(authorizePayment.complete(new MerchantId(merchant), new PaymentId(id)));
    }

    @PostMapping("/payments/{id}/capture")
    ResponseEntity<String> capture(@RequestHeader(MERCHANT) String merchant, @PathVariable String id,
                                   @RequestHeader(name = KEY, required = false) String key,
                                   @RequestBody(required = false) String body) {
        MerchantId merchantId = new MerchantId(merchant);
        PaymentId paymentId = new PaymentId(id);
        return idempotent.execute(merchantId, "/payments/" + id + "/capture", key, text(body), () -> {
            Money amount = amount(merchantId, paymentId, body);
            return operationOutcome(operations.capture(merchantId, paymentId, amount));
        });
    }

    @PostMapping("/payments/{id}/cancel")
    ResponseEntity<String> cancel(@RequestHeader(MERCHANT) String merchant, @PathVariable String id,
                                  @RequestHeader(name = KEY, required = false) String key) {
        MerchantId merchantId = new MerchantId(merchant);
        PaymentId paymentId = new PaymentId(id);
        return idempotent.execute(merchantId, "/payments/" + id + "/cancel", key, "",
                () -> operationOutcome(operations.cancel(merchantId, paymentId)));
    }

    @PostMapping("/payments/{id}/refund")
    ResponseEntity<String> refund(@RequestHeader(MERCHANT) String merchant, @PathVariable String id,
                                  @RequestHeader(name = KEY, required = false) String key,
                                  @RequestBody(required = false) String body) {
        MerchantId merchantId = new MerchantId(merchant);
        PaymentId paymentId = new PaymentId(id);
        return idempotent.execute(merchantId, "/payments/" + id + "/refund", key, text(body), () -> {
            Money amount = amount(merchantId, paymentId, body);
            return operationOutcome(operations.refund(merchantId, paymentId, amount));
        });
    }

    @GetMapping("/payments/{id}/history")
    List<AuditEntry> history(@RequestHeader(MERCHANT) String merchant, @PathVariable String id) {
        PaymentId paymentId = new PaymentId(id);
        authorizePayment.current(new MerchantId(merchant), paymentId); // 404 for another merchant's payment
        return audit.forPayment(paymentId);
    }

    private Money amount(MerchantId merchantId, PaymentId paymentId, String body) {
        String currency = authorizePayment.current(merchantId, paymentId).payment().amount().currency().getCurrencyCode();
        return body == null || body.isBlank() ? null
                : PaymentRequests.money(parse(body, PaymentRequests.Amount.class), currency);
    }

    private static String text(String body) {
        return body == null ? "" : body;
    }

    private <T> T parse(String body, Class<T> type) {
        try {
            return json.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON");
        }
    }

    private ResponseEntity<PaymentResponse> lifecycle(PaymentResult result) {
        HttpStatus status = switch (result.payment().status()) {
            case AUTHORIZED, CAPTURED, CANCELLED, REFUNDED -> HttpStatus.OK;
            case DECLINED -> HttpStatus.PAYMENT_REQUIRED;
            case ACTION_REQUIRED, UNKNOWN, PROCESSING, PENDING, CREATED -> HttpStatus.ACCEPTED;
            case FAILED -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(response(result));
    }

    /** The answer to a capture, cancel or refund depends on that operation, not on the payment. */
    private ResponseEntity<PaymentResponse> operationOutcome(PaymentResult result) {
        PaymentOperation operation = result.payment().findOperation(result.operationId()).orElseThrow();
        HttpStatus status = switch (operation.status()) {
            case SUCCEEDED -> HttpStatus.OK;
            case FAILED -> HttpStatus.BAD_GATEWAY;
            case IN_FLIGHT, UNKNOWN -> HttpStatus.ACCEPTED;
        };
        return ResponseEntity.status(status).body(response(result));
    }

    private PaymentResponse response(PaymentResult result) {
        return PaymentResponse.from(result, operations.allowedActions(result.payment()));
    }
}
