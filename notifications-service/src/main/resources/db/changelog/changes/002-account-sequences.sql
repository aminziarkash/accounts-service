--liquibase formatted sql

--changeset amin:002-account-sequences
CREATE TABLE account_sequences (
    account_key TEXT   PRIMARY KEY,
    last_seq    BIGINT NOT NULL
);