package com.designpatterns.level09.presentation.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * FLOW A.1 (and every other HTTP request): INTERCEPTING FILTER. Runs BEFORE any controller.
 *
 * <p>Every request gets a correlation id, put in the logging context (MDC) and echoed back in the
 * response. Every log line of this request then carries the same id, so you can find all of them.
 * A filter is the right place for things that apply to ALL requests, so no controller repeats them.
 *
 * <p>{@code @Component} is enough: Spring Boot registers every {@code Filter} bean in the web server.
 * {@code OncePerRequestFilter} guarantees it runs once even if the request is forwarded internally.
 */
@Component
class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || id.isBlank()) {
            id = "cid_" + UUID.randomUUID();
        }
        MDC.put("correlationId", id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response); // continue to the next filter, and finally the controller
        } finally {
            MDC.remove("correlationId"); // threads are reused: never leak the id into the next request
        }
    }
}
