/**
 * <h2>PRESENTATION layer: every way the outside world starts something.</h2>
 *
 * <ul>
 *   <li>{@code rest}: the merchant API ({@code PaymentController}) and the admin API. FLOW A and FLOW B start here.</li>
 *   <li>{@code webhook}: one endpoint that providers call. FLOW C starts here.</li>
 *   <li>{@code scheduling}: a timer is also an entry point. FLOW D starts here (or from the admin API).</li>
 * </ul>
 *
 * <p><b>Job:</b> translate between the outside world and services: read headers and JSON into DTOs, call ONE
 * service method, turn the result (or exception) into a status code and JSON. No business rule lives here.
 *
 * <p><b>Patterns:</b> MVC Controller, DTO (request/response shapes separate from the model), Exception
 * Translation ({@code ApiExceptionHandler}), Intercepting Filter ({@code CorrelationIdFilter}),
 * Execute-Around ({@code IdempotentRequestExecutor}).
 *
 * <p><b>Annotations:</b> {@code @RestController} (a controller whose return values become the HTTP body),
 * {@code @PostMapping}/{@code @GetMapping} (which URL and HTTP method), {@code @PathVariable} (a part of
 * the URL), {@code @RequestHeader}, {@code @RequestBody} (the raw body or JSON), {@code @RestControllerAdvice}
 * + {@code @ExceptionHandler} (one place that turns exceptions into responses), {@code @Scheduled} (run a
 * method on a timer), {@code @ConditionalOnProperty} (create the bean only if a setting is true).
 */
package com.designpatterns.level09.presentation;
