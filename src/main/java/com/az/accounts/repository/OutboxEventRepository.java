package com.az.accounts.repository;

import com.az.accounts.domain.OutboxEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(
            value =
                    "select * from outbox_events where published_at is null"
                            + " order by id limit :batchSize for update skip locked",
            nativeQuery = true)
    List<OutboxEvent> lockUnpublishedBatch(@Param("batchSize") int batchSize);

    long countByPublishedAtIsNull();

    @Query("select min(e.createdAt) from OutboxEvent e where e.publishedAt is null")
    Instant findOldestPendingCreatedAt();

    // true if this transaction got the lock, false if another relay already has it; released at
    // commit or rollback
    @Query(value = "select pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    boolean tryRelayLock(@Param("key") long key);
}
