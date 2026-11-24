package com.az.notifications;

public class OutOfOrderEventException extends RuntimeException {

    public OutOfOrderEventException(String key, long sequence) {
        super("Event with sequence %d for key %s is older than one already applied".formatted(sequence, key));
    }
}