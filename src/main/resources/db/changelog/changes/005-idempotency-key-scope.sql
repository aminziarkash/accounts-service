--liquibase formatted sql

--changeset amin:005-idempotency-key-scope
-- the stored key is now "<caller subject>:<Idempotency-Key header>", so it needs more room
ALTER TABLE idempotency_record ALTER COLUMN idempotency_key TYPE VARCHAR(200);
