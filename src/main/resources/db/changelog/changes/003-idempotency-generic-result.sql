--liquibase formatted sql

--changeset amin:003-idempotency-generic-result
ALTER TABLE idempotency_record RENAME COLUMN transfer_reference TO result_ref;
ALTER TABLE idempotency_record ALTER COLUMN result_ref TYPE VARCHAR(64) USING result_ref::text;
