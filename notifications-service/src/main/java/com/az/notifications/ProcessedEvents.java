package com.az.notifications;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class ProcessedEvents {

    private final JdbcClient jdbc;

    public ProcessedEvents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns true if the event is new, false if it was already processed. */
    public boolean markProcessed(UUID eventId) {
        int inserted = jdbc.sql("insert into processed_events (event_id) values (:id) on conflict do nothing")
                .param("id", eventId)
                .update();
        return inserted == 1;
    }
}