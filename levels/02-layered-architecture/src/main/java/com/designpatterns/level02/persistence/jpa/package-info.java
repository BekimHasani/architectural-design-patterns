/**
 * <h2>The JPA implementation of the repositories. Everything here is package-private.</h2>
 *
 * <p>Three kinds of class, for each table:
 * <pre>
 *   XxxEntity           the table row, with JPA annotations            (persistence type)
 *   SpringDataXxx       interface; Spring Data writes the SQL for it   (generated at startup)
 *   JpaXxxRepository    implements the persistence interface; maps     (ADAPTER + DATA MAPPER)
 *                       Entity ⇄ model object
 * </pre>
 *
 * <p><b>Why entities are separate from the model:</b> JPA needs a no-arg constructor, mutable fields and
 * annotations. Putting those on {@code Payment} would let anybody bypass its rules. So the model stays
 * pure, and the adapter copies data between the two (the <b>Data Mapper</b> pattern). More code, but the
 * table layout can change without touching any business rule.
 *
 * <p><b>Annotations used here, in simple words:</b>
 * <ul>
 *   <li>{@code @Entity}, {@code @Table(name)}: "this class is a row of that table".</li>
 *   <li>{@code @Id}, {@code @IdClass}: the primary key (a composite key uses {@code @IdClass}).</li>
 *   <li>{@code @Column(name = "...")}: the column name when it differs from the field name.</li>
 *   <li>{@code @Transient}: a field JPA must NOT store (our {@code isNew} flag).</li>
 *   <li>{@code @PostLoad}, {@code @PostPersist}: JPA calls these methods after loading / inserting.</li>
 *   <li>{@code @GeneratedValue(strategy = IDENTITY)}: the database creates the id (event sequence).</li>
 *   <li>{@code @Query}: write the JPQL query yourself instead of deriving it from the method name.</li>
 *   <li>{@code @Lock(PESSIMISTIC_WRITE)}: {@code SELECT ... FOR UPDATE}, the row stays locked until commit.</li>
 *   <li>{@code @Modifying} + {@code @Transactional}: an UPDATE query, run in its own short transaction.</li>
 *   <li>{@code @Repository}: a Spring bean for data access; Spring also translates database exceptions
 *       into {@code DataAccessException}s such as {@code DataIntegrityViolationException}.</li>
 * </ul>
 *
 * <p><b>Decisions to remember:</b>
 * <ul>
 *   <li><b>{@code Persistable}</b>: an entity with an id we choose ourselves looks "already stored" to
 *       Spring Data, so {@code save()} would MERGE (overwrite). Implementing {@code isNew()} makes it a real
 *       INSERT, and a duplicate key fails loudly. The inbox and idempotency keys depend on that failure.</li>
 *   <li><b>No {@code @Version}</b> on payments: see {@code JpaPaymentRepository}. We lock the row, compare the
 *       version ourselves and add exactly 1 on every save.</li>
 *   <li><b>{@code TransactionTemplate}</b> instead of {@code @Transactional} on services: transactions are
 *       short and live only here, never around a call to a provider.</li>
 * </ul>
 */
package com.designpatterns.level02.persistence.jpa;
