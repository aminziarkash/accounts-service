--liquibase formatted sql

--changeset amin:002-idempotency-records
CREATE TABLE idempotency_record (
    idempotency_key    VARCHAR(100) PRIMARY KEY,
    request_hash       VARCHAR(64)  NOT NULL,
    transfer_reference UUID,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);