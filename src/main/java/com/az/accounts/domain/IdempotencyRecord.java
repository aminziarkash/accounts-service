package com.az.accounts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

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
}
