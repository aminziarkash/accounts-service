--liquibase formatted sql

--changeset amin:004-customer-owner
-- the Keycloak "sub" of the user who owns the customer; null for rows created before security existed
ALTER TABLE customer ADD COLUMN owner_subject VARCHAR(64);
CREATE INDEX idx_customer_owner_subject ON customer (owner_subject);
