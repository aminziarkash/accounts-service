package com.az.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID eventId;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    protected Notification() {}

    public Notification(UUID eventId, Long accountId, String eventType, String message) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.eventType = eventType;
        this.message = message;
    }

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public Long getAccountId() { return accountId; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}