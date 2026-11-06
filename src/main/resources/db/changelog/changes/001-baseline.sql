--liquibase formatted sql

--changeset amin:001-baseline
CREATE TABLE customer (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE account (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT         NOT NULL REFERENCES customer (id),
    currency    VARCHAR(3)     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    balance     NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_account_customer_id ON account (customer_id);

CREATE TABLE ledger_entry (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id         BIGINT         NOT NULL REFERENCES account (id),
    type               VARCHAR(20)    NOT NULL,
    amount             NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    balance_after      NUMERIC(19, 2) NOT NULL,
    transfer_reference UUID,
    created_at         TIMESTAMPTZ    NOT NULL  DEFAULT now()
);

CREATE INDEX idx_ledger_entry_account_created ON ledger_entry (account_id, created_at);

--rollback DROP TABLE ledger_entry;
--rollback DROP TABLE account;
--rollback DROP TABLE customer;