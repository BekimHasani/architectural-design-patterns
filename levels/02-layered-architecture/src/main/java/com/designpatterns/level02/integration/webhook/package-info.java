/**
 * <h2>Webhooks: providers calling US (FLOW C).</h2>
 *
 * <p>A provider sends an HTTP request when something changes (payment succeeded, refund done). Each
 * provider signs it differently and uses a different JSON shape.
 *
 * <ul>
 *   <li><b>Strategy</b> ({@link com.designpatterns.level02.integration.webhook.ProviderEventTranslator}): one
 *       implementation per provider: check the signature, then turn the body into our
 *       {@code ProviderEvent}s.</li>
 *   <li><b>Registry</b> ({@code WebhookTranslators}): finds the strategy for a provider id. Spring injects a
 *       {@code List} of every translator bean, so adding a provider needs no change here.</li>
 *   <li><b>Sealed result</b> ({@code WebhookTranslation}): Rejected (bad signature), Unreadable, or Events.</li>
 * </ul>
 * In Level 1 each provider had its own {@code @RestController}. In a layered design only presentation may
 * have controllers, so there is one endpoint ({@code presentation.webhook.ProviderWebhookController}) and
 * the provider-specific part became these plain classes.
 */
package com.designpatterns.level02.integration.webhook;
