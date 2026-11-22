package com.designpatterns.level03.presentation.rest;

import com.designpatterns.level03.model.Money;
import com.designpatterns.level03.model.dto.CreatePaymentRequest;
import com.designpatterns.level03.model.dto.PaymentResponse;
import com.designpatterns.level03.model.dto.RefundPaymentRequest;
import com.designpatterns.level03.service.idempotency.IdempotencyService;
import com.designpatterns.level03.service.payment.ChargeCommand;
import com.designpatterns.level03.service.payment.PaymentService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * POST /payments, GET /payments/{id}, POST /payments/{id}/refunds.
 * {@code X-Merchant-Id} says who calls; money-moving commands need an {@code Idempotency-Key}.
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotency;

    public PaymentController(PaymentService paymentService, IdempotencyService idempotency) {
        this.paymentService = paymentService;
        this.idempotency = idempotency;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(@RequestHeader("X-Merchant-Id") String merchantId,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                  @RequestBody CreatePaymentRequest request) {
        if (request.customerId() == null || request.customerId().isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (request.currency() == null || request.currency().length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO code");
        }
        if (request.amountMinor() == null || request.amountMinor() <= 0) {
            throw new IllegalArgumentException("amountMinor must be above zero");
        }
        Money amount = Money.of(request.amountMinor(), request.currency().toUpperCase());

        String paymentId = idempotency.execute(merchantId, "POST /payments", idempotencyKey,
                () -> paymentService.charge(new ChargeCommand(merchantId, request.customerId(), amount)).id());

        return ResponseEntity.created(URI.create("/payments/" + paymentId)).body(paymentService.find(merchantId, paymentId));
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse get(@RequestHeader("X-Merchant-Id") String merchantId, @PathVariable String paymentId) {
        return paymentService.find(merchantId, paymentId);
    }

    @PostMapping("/{paymentId}/refunds")
    public PaymentResponse refund(@RequestHeader("X-Merchant-Id") String merchantId,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                  @PathVariable String paymentId,
                                  @RequestBody RefundPaymentRequest request) {
        if (request.amountMinor() == null || request.amountMinor() <= 0) {
            throw new IllegalArgumentException("amountMinor must be above zero");
        }
        PaymentResponse current = paymentService.find(merchantId, paymentId);
        Money amount = Money.of(request.amountMinor(), current.currency());

        idempotency.execute(merchantId, "POST /payments/" + paymentId + "/refunds", idempotencyKey,
                () -> paymentService.refund(merchantId, paymentId, amount, request.reason()).id());

        return paymentService.find(merchantId, paymentId);
    }
}
