/**
 * <h2>The heart of the application: authorizing and operating on payments (FLOW A and FLOW B).</h2>
 *
 * <pre>
 *  PaymentService.authorize ─► ProviderRoutingService (who may take it?)
 *        │                     MerchantService (credentials per provider)
 *        └─► AuthorizationOrchestrator ─► ProviderGateway ─► provider adapter
 *                  │
 *                  └─► GatewayResultApplier (provider answer → Payment method)
 *
 *  PaymentOperationService.capture/cancel/refund ─► ProviderGateway ─► GatewayResultApplier
 * </pre>
 * <ul>
 *   <li>{@code PaymentService}: entry for authorize / complete / read. Validates, routes, creates.</li>
 *   <li>{@code AuthorizationOrchestrator}: <b>Orchestrator</b>. Tries providers in order, with write-ahead,
 *       and falls back ONLY when the failure proves nothing was charged.</li>
 *   <li>{@code PaymentOperationService}: capture, cancel, refund; same three steps each time
 *       (reserve + commit, call, record + commit).</li>
 *   <li>{@code GatewayResultApplier}: the single place that turns a {@code GatewayResult} into a change of the
 *       {@code Payment}. Exhaustive {@code switch} over a sealed type.</li>
 *   <li>{@code PaymentResult}: what a use case returns to the controller (payment + attempts).</li>
 * </ul>
 */
package com.designpatterns.level09.service.payment;
