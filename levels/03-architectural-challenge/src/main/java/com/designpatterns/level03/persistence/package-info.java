/**
 * PERSISTENCE layer: storing payments and idempotency keys.
 * The interfaces here speak model types; {@code jpa/} implements them with Spring Data JPA.
 * Nothing outside {@code persistence.jpa} sees an {@code @Entity}.
 */
package com.designpatterns.level03.persistence;
