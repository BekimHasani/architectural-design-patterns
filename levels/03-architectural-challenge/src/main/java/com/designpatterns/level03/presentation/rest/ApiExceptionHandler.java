package com.designpatterns.level03.presentation.rest;

import com.designpatterns.level03.model.exceptions.PaymentNotFoundException;
import com.designpatterns.level03.model.exceptions.PaymentRuleViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Exceptions → RFC 7807 ProblemDetail responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail notFound(PaymentNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PaymentRuleViolationException.class)
    ProblemDetail ruleViolation(PaymentRuleViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
