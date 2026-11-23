package com.az.accounts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord implements Persistable {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(nullable = false)
    private String requestHash;

    /** Points to what the first request produced: a transfer reference or a ledger entry id. */
    @Column(length = 64)
    private String resultRef;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    // true until the row has been inserted or loaded: tells Spring Data to INSERT, never merge
    @Transient private boolean newRecord = true;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String idempotencyKey, String requestHash) {
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
    }

    public void complete(String resultRef) {
        this.resultRef = Objects.requireNonNull(resultRef, "resultRef");
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResultRef() {
        return resultRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String getId() {
        return idempotencyKey;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.newRecord = false;
    }
}
