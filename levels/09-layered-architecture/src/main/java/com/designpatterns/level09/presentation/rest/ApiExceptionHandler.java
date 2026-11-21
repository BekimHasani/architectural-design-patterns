package com.designpatterns.level09.presentation.rest;

import com.designpatterns.level09.model.exceptions.ConcurrentUpdateException;
import com.designpatterns.level09.model.exceptions.CurrencyMismatchException;
import com.designpatterns.level09.model.exceptions.IllegalPaymentTransitionException;
import com.designpatterns.level09.model.exceptions.PaymentRuleViolationException;
import com.designpatterns.level09.service.exception.NoProviderAvailableException;
import com.designpatterns.level09.service.exception.OperationNotSupportedException;
import com.designpatterns.level09.service.exception.UnknownMerchantException;
import com.designpatterns.level09.service.exception.UnknownPaymentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * EXCEPTION TRANSLATION at the edge: service and model exceptions → RFC 9457 problem responses. The
 * layers below throw in business words; only this class knows HTTP status codes.
 *
 * <p>When any controller method throws, Spring MVC looks here for a matching {@code @ExceptionHandler}.
 * {@code @RestControllerAdvice} makes the handlers apply to ALL controllers, so no controller needs
 * try/catch. Returning a {@code ProblemDetail} gives a standard JSON error body
 * ({@code type, title, status, detail}).
 *
 * <p>Example: two refunds race; the loser's save throws {@code ConcurrentUpdateException} (persistence),
 * which travels up through the service untouched and becomes 409 Conflict here.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class, CurrencyMismatchException.class})
    ProblemDetail invalidRequest(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnknownPaymentException.class)
    ProblemDetail unknownPayment(UnknownPaymentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnknownMerchantException.class)
    ProblemDetail unknownMerchant(UnknownMerchantException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler({NoProviderAvailableException.class, OperationNotSupportedException.class,
            PaymentRuleViolationException.class})
    ProblemDetail unprocessable(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler({ConcurrentUpdateException.class, IllegalPaymentTransitionException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
