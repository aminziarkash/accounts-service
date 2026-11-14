package com.az.accounts.service;

import com.az.accounts.config.IdempotencyProperties;
import com.az.accounts.repository.IdempotencyRecordRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes idempotency keys older than the retention period so the table does not grow forever. */
@Component
public class IdempotencyCleanup {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanup.class);

    private final IdempotencyRecordRepository records;
    private final IdempotencyProperties properties;

    public IdempotencyCleanup(
            IdempotencyRecordRepository records, IdempotencyProperties properties) {
        this.records = records;
        this.properties = properties;
    }

    // every instance runs this; deleting the same old rows twice is harmless
    @Scheduled(
            initialDelayString = "${app.idempotency.cleanup-interval:PT1H}",
            fixedDelayString = "${app.idempotency.cleanup-interval:PT1H}")
    public void run() {
        deleteOlderThan(Instant.now().minus(properties.retention()));
    }

    // no @Transactional here: run() calls this method on the same object, so the proxy would be
    // skipped. The repository method carries the transaction.
    public int deleteOlderThan(Instant cutoff) {
        int deleted = records.deleteCreatedBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} expired idempotency keys", deleted);
        }
        return deleted;
    }
}
