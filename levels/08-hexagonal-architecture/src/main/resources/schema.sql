-- One row per payment. `version` makes concurrent writers fail instead of overwriting each other.
CREATE TABLE IF NOT EXISTS payments (
    id            VARCHAR(64)   PRIMARY KEY,
    merchant_id   VARCHAR(64)   NOT NULL,
    customer_id   VARCHAR(64)   NOT NULL,
    currency      CHAR(3)       NOT NULL,
    amount_minor  BIGINT        NOT NULL,
    capture_mode  VARCHAR(16)   NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    provider_id   VARCHAR(32),
    provider_ref  VARCHAR(128),
    attempt_id    VARCHAR(64),
    redirect_url  VARCHAR(1024),
    reason        VARCHAR(255),
    version       INT           NOT NULL,
    updated_at    TIMESTAMP     NOT NULL
);
CREATE INDEX IF NOT EXISTS payments_by_status ON payments (status);

-- Captures, cancels and refunds. Written IN_FLIGHT before the provider call.
CREATE TABLE IF NOT EXISTS payment_operations (
    operation_id  VARCHAR(64)   PRIMARY KEY,
    payment_id    VARCHAR(64)   NOT NULL,
    type          VARCHAR(16)   NOT NULL,
    amount_minor  BIGINT,
    status        VARCHAR(16)   NOT NULL,
    provider_ref  VARCHAR(128),
    reason        VARCHAR(255),
    position      INT           NOT NULL
);
CREATE INDEX IF NOT EXISTS operations_by_payment ON payment_operations (payment_id);
CREATE INDEX IF NOT EXISTS operations_by_status ON payment_operations (status);

-- AUDIT: append-only, written in the SAME transaction as the state change it describes.
CREATE TABLE IF NOT EXISTS payment_events (
    seq           BIGINT        AUTO_INCREMENT PRIMARY KEY,
    payment_id    VARCHAR(64)   NOT NULL,
    type          VARCHAR(48)   NOT NULL,
    from_status   VARCHAR(20)   NOT NULL,
    to_status     VARCHAR(20)   NOT NULL,
    amount_minor  BIGINT,
    actor         VARCHAR(96)   NOT NULL,
    detail        VARCHAR(512),
    occurred_at   TIMESTAMP     NOT NULL
);
CREATE INDEX IF NOT EXISTS events_by_payment ON payment_events (payment_id);

CREATE TABLE IF NOT EXISTS payment_attempts (
    attempt_id   VARCHAR(64)  PRIMARY KEY,
    payment_id     VARCHAR(64)  NOT NULL,
    provider_id  VARCHAR(32)  NOT NULL,
    started_at   TIMESTAMP    NOT NULL,
    duration_ms  BIGINT       NOT NULL,
    outcome      VARCHAR(32)  NOT NULL
);
CREATE INDEX IF NOT EXISTS payment_attempts_by_payment ON payment_attempts (payment_id);

-- Client → API idempotency. Keys are scoped by merchant: see IdempotentRequests.
CREATE TABLE IF NOT EXISTS idempotency_keys (
    idem_key       VARCHAR(255) PRIMARY KEY,
    fingerprint    VARCHAR(64)  NOT NULL,
    state          VARCHAR(16)  NOT NULL,
    http_status    INT,
    response_body  CLOB,
    created_at     TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS provider_event_inbox (
    provider_id  VARCHAR(32)   NOT NULL,
    event_id     VARCHAR(255)  NOT NULL,
    payload      CLOB          NOT NULL,
    received_at  TIMESTAMP     NOT NULL,
    outcome      VARCHAR(32),
    PRIMARY KEY (provider_id, event_id)
);
