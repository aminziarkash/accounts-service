--liquibase formatted sql

--changeset amin:001-notifications
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    event_id   UUID         NOT NULL UNIQUE,
    account_id BIGINT       NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    message    TEXT         NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_account ON notifications (account_id, id);