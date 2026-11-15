package com.az.accounts.domain;

/**
 * These strings are a contract. The enum names will be sent to Kafka as the event type, so renaming a constant later breaks consumers, just like renaming a field.
 */
public enum EventType {
    MONEY_DEPOSITED,
    MONEY_WITHDRAWN
}
