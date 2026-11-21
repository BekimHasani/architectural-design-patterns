package com.designpatterns.level01.api;

import com.designpatterns.level01.payments.application.commands.NoProviderAvailableException;
import com.designpatterns.level01.payments.application.commands.OperationNotSupportedException;
import com.designpatterns.level01.payments.application.commands.UnknownMerchantException;
import com.designpatterns.level01.payments.application.commands.UnknownPaymentException;
import com.designpatterns.level01.payments.application.ports.ConcurrentUpdateException;
import com.designpatterns.level01.payments.domain.CurrencyMismatchException;
import com.designpatterns.level01.payments.domain.IllegalPaymentTransitionException;
import com.designpatterns.level01.payments.domain.PaymentRuleViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps invalid input, rule violations and races to RFC 9457 problem responses. */
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
