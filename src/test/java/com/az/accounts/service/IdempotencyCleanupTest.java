package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.domain.IdempotencyRecord;
import com.az.accounts.repository.IdempotencyRecordRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdempotencyCleanupTest {

    @Autowired private IdempotencyCleanup cleanup;
    @Autowired private IdempotencyRecordRepository records;

    @BeforeEach
    @AfterEach
    void clean() {
        records.deleteAllInBatch();
    }

    @Test
    void recordsOlderThanTheCutoffAreDeleted() {
        records.saveAndFlush(new IdempotencyRecord("old-key", "hash"));

        // a cutoff in the future makes the record "old"
        int deleted = cleanup.deleteOlderThan(Instant.now().plus(Duration.ofHours(1)));

        assertThat(deleted).isEqualTo(1);
        assertThat(records.findById("old-key")).isEmpty();
    }

    @Test
    void recentRecordsAreKept() {
        records.saveAndFlush(new IdempotencyRecord("fresh-key", "hash"));

        int deleted = cleanup.deleteOlderThan(Instant.now().minus(Duration.ofHours(24)));

        assertThat(deleted).isZero();
        assertThat(records.findById("fresh-key")).isPresent();
    }
}
