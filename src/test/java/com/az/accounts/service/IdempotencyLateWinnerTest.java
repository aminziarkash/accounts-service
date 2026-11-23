package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.repository.IdempotencyRecordRepository;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// The race, made deterministic: request 2 has already passed the replay check when request 1
// commits. Request 2 must notice the stored key and replay, not run the action again.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdempotencyLateWinnerTest {

    @Autowired private IdempotencyService idempotency;
    @Autowired private IdempotencyRecordRepository records;

    @AfterEach
    void cleanUp() {
        records.deleteAllInBatch();
    }

    @Test
    void aWinnerThatCommitsAfterTheReplayCheckIsNotRunAgain() {
        AtomicInteger runs = new AtomicInteger();
        Supplier<String> action = () -> "result-" + runs.incrementAndGet();
        Function<String, String> same = value -> value;

        String second =
                idempotency.execute(
                        "late-winner",
                        "hash-1",
                        // request 1 runs and commits here, after request 2 saw no key
                        () -> idempotency.execute("late-winner", "hash-1", action, same, same),
                        action,
                        same,
                        same);

        assertThat(runs.get()).isEqualTo(1); // the action ran once
        assertThat(second).isEqualTo("result-1"); // request 2 got request 1's result
        assertThat(records.findById("late-winner").orElseThrow().getResultRef())
                .isEqualTo("result-1"); // and did not overwrite the stored result
    }
}
