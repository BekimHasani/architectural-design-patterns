-- schema.sql owns the tables; Hibernate runs with ddl-auto: none.
CREATE TABLE IF NOT EXISTS payments (
    id              VARCHAR(64)   PRIMARY KEY,
    merchant_id     VARCHAR(64)   NOT NULL,
    customer_id     VARCHAR(64)   NOT NULL,
    currency        CHAR(3)       NOT NULL,
    amount_minor    BIGINT        NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    provider_id     VARCHAR(32),
    provider_ref    VARCHAR(128),
    refunded_minor  BIGINT        NOT NULL,
    failure_reason  VARCHAR(1024),
    updated_at      TIMESTAMP     NOT NULL
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    idem_key    VARCHAR(255)  PRIMARY KEY,
    result      VARCHAR(64)   NOT NULL,
    created_at  TIMESTAMP     NOT NULL
);
