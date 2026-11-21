/**
 * INTEGRATION layer: talking to payment providers.
 * <ul>
 *   <li>{@code gateway/}: the contract every provider implements, and the registry that finds one by id.</li>
 *   <li>{@code support/}: HTTP plumbing shared by the adapters.</li>
 *   <li>{@code provider/<name>/}: one ADAPTER per provider. Only the gateway class is public.</li>
 * </ul>
 */
package com.designpatterns.level03.integration;
