package com.designpatterns.level02.presentation.rest;

import com.designpatterns.level02.model.MerchantId;
import com.designpatterns.level02.model.Money;
import com.designpatterns.level02.model.PaymentId;
import com.designpatterns.level02.model.PaymentOperation;
import com.designpatterns.level02.presentation.rest.dto.AmountDto;
import com.designpatterns.level02.presentation.rest.dto.AuthorizeRequestDto;
import com.designpatterns.level02.presentation.rest.dto.PaymentResponseDto;
import com.designpatterns.level02.service.audit.AuditEntry;
import com.designpatterns.level02.service.audit.AuditService;
import com.designpatterns.level02.service.payment.PaymentOperationService;
import com.designpatterns.level02.service.payment.PaymentResult;
import com.designpatterns.level02.service.payment.PaymentService;
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
 * The platform's public API (MVC controller). It only translates HTTP ↔ service calls: parse the DTO,
 * call a service, map the result to a DTO and a status code. No business rule lives here.
 * {@code X-Merchant-Id} says who is calling; real authentication would sit in front of this. A payment
 * of another merchant answers 404.
 *
 * <p>FLOW A starts in {@link #authorize}; FLOW B in {@link #capture}, {@link #cancel}, {@link #refund}.
 *
 * <p>{@code @RestController} = {@code @Controller} + {@code @ResponseBody}: what a method returns is written
 * as the HTTP body (as JSON), not used as a view name. The class is package-private: nobody calls it
 * from Java, only Spring MVC does, through the URL mappings.
 *
 * <p>Why the body is taken as a {@code String} and parsed by hand: the idempotency check needs the EXACT
 * bytes the client sent (to fingerprint them) before anything else happens.
 */
@RestController
class PaymentController {

    private static final String MERCHANT = "X-Merchant-Id";
    private static final String KEY = "Idempotency-Key";

    private final PaymentService payments;
    private final PaymentOperationService operations;
    private final AuditService audit;
    private final IdempotentRequestExecutor idempotent;
    private final ObjectMapper json;

    PaymentController(PaymentService payments, PaymentOperationService operations, AuditService audit,
                      IdempotentRequestExecutor idempotent, ObjectMapper json) {
        this.payments = payments;
        this.operations = operations;
        this.audit = audit;
        this.idempotent = idempotent;
        this.json = json;
    }

    /**
     * FLOW A.2: {@code POST /payments}, a merchant asks us to take money from a customer.
     * <ul>
     *   <li>{@code @PostMapping("/payments")}: Spring MVC calls this method for that URL and HTTP method.</li>
     *   <li>{@code @RequestHeader(MERCHANT)}: copies the {@code X-Merchant-Id} header in; missing → 400.</li>
     *   <li>{@code @RequestHeader(required = false)}: the key may be missing; the executor answers 400 itself
     *       with a clear message.</li>
     *   <li>{@code @RequestBody String}: the raw request body.</li>
     * </ul>
     */
    @PostMapping("/payments")
    ResponseEntity<String> authorize(@RequestHeader(MERCHANT) String merchant,
                                     @RequestHeader(name = KEY, required = false) String key,
                                     @RequestBody String body) {
        MerchantId merchantId = new MerchantId(merchant);
        // FLOW A.3: EXECUTE AROUND. The executor claims the Idempotency-Key, runs the lambda only for the
        // first request, and stores its answer. A retry by the client gets the stored answer back.
        return idempotent.execute(merchantId, "/payments", key, body, () -> {
            // FLOW A.4: JSON → DTO → model value objects (Money). DTOs never go further down than here.
            AuthorizeRequestDto request = parse(body, AuthorizeRequestDto.class);
            // FLOW A.5 → PaymentService.authorize; FLOW A.15: result → HTTP status (lifecycle)
            return lifecycle(payments.authorize(merchantId, request.customerId(),
                    Money.of(request.amountMinor(), request.currency()), request.captureMode(), request.alsoRequired()));
        });
    }

    @GetMapping("/payments/{id}")
    ResponseEntity<PaymentResponseDto> current(@RequestHeader(MERCHANT) String merchant, @PathVariable String id) {
        return lifecycle(payments.current(new MerchantId(merchant), new PaymentId(id)));
    }

    @PostMapping("/payments/{id}/complete")
    ResponseEntity<PaymentResponseDto> complete(@RequestHeader(MERCHANT) String merchant, @PathVariable String id) {
        return lifecycle(payments.complete(new MerchantId(merchant), new PaymentId(id)));
    }

    /** FLOW B.1: capture (take money that was held). {@code @PathVariable} reads {id} from the URL. */
    @PostMapping("/payments/{id}/capture")
    ResponseEntity<String> capture(@RequestHeader(MERCHANT) String merchant, @PathVariable String id,
                                   @RequestHeader(name = KEY, required = false) String key,
                                   @RequestBody(required = false) String body) {
        MerchantId merchantId = new MerchantId(merchant);
        PaymentId paymentId = new PaymentId(id);
        // the PATH is part of the key scope: the same key can't be replayed against another command
        return idempotent.execute(merchantId, "/payments/" + id + "/capture", key, text(body), () -> {
            Money amount = amount(merchantId, paymentId, body);
            return operationOutcome(operations.capture(merchantId, paymentId, amount));
        });
    }

    /** FLOW B.1: cancel (release held money). */
    @PostMapping("/payments/{id}/cancel")
    ResponseEntity<String> cancel(@RequestHeader(MERCHANT) String merchant, @PathVariable String id,
                                  @RequestHeader(name = KEY, required = false) String key) {
        MerchantId merchantId = new MerchantId(merchant);
        PaymentId paymentId = new PaymentId(id);
        return idempotent.execute(merchantId, "/payments/" + id + "/cancel", key, "",
                () -> operationOutcome(operations.cancel(merchantId, paymentId)));
    }

    /** FLOW B.1: refund (give captured money back, fully or partly). */
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
        return audit.history(new MerchantId(merchant), new PaymentId(id));
    }

    private Money amount(MerchantId merchantId, PaymentId paymentId, String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String currency = payments.current(merchantId, paymentId).payment().amount().currency().getCurrencyCode();
        return parse(body, AmountDto.class).toMoney(currency);
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

    /**
     * FLOW A.15: the payment's status decides the HTTP status. 202 Accepted for "not final yet" (UNKNOWN,
     * ACTION_REQUIRED, ...) tells the client to look again later; 402 for a decline; 502 when the provider
     * failed. The exhaustive {@code switch} won't compile if a new status is added and forgotten here.
     */
    private ResponseEntity<PaymentResponseDto> lifecycle(PaymentResult result) {
        HttpStatus status = switch (result.payment().status()) {
            case AUTHORIZED, CAPTURED, CANCELLED, REFUNDED -> HttpStatus.OK;
            case DECLINED -> HttpStatus.PAYMENT_REQUIRED;
            case ACTION_REQUIRED, UNKNOWN, PROCESSING, PENDING, CREATED -> HttpStatus.ACCEPTED;
            case FAILED -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(response(result));
    }

    /** FLOW B.7: the answer to a capture, cancel or refund depends on that operation, not on the payment. */
    private ResponseEntity<PaymentResponseDto> operationOutcome(PaymentResult result) {
        PaymentOperation operation = result.payment().findOperation(result.operationId()).orElseThrow();
        HttpStatus status = switch (operation.status()) {
            case SUCCEEDED -> HttpStatus.OK;
            case FAILED -> HttpStatus.BAD_GATEWAY;
            case IN_FLIGHT, UNKNOWN -> HttpStatus.ACCEPTED;
        };
        return ResponseEntity.status(status).body(response(result));
    }

    private PaymentResponseDto response(PaymentResult result) {
        return PaymentResponseDto.from(result, operations.allowedActions(result.payment()));
    }
}
