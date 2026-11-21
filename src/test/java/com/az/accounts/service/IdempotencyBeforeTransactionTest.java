package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.az.accounts.domain.IdempotencyRecord;
import com.az.accounts.exception.LimitsUnavailableException;
import com.az.accounts.repository.IdempotencyRecordRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

// The promise of the hook: before the transaction, never for a replay, and a failing hook means
// nothing is stored and no transaction is ever opened.
@ExtendWith(MockitoExtension.class)
class IdempotencyBeforeTransactionTest {

    @Mock private IdempotencyRecordRepository records;
    @Mock private TransactionTemplate transactionTemplate;

    private IdempotencyService service;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(records, transactionTemplate);
    }

    @Test
    void hookRunsBeforeTheTransactionOpens() {
        when(records.findById("k")).thenReturn(Optional.empty());
        when(transactionTemplate.<String>execute(any()))
                .thenAnswer(invocation -> {
                    events.add("transaction");
                    return "done";
                });

        String result =
                service.execute("k", "h", () -> events.add("hook"), () -> "x", s -> s, s -> s);

        assertThat(result).isEqualTo("done");
        assertThat(events).containsExactly("hook", "transaction");
    }

    @Test
    void hookNeverRunsForAReplay() {
        IdempotencyRecord finished = new IdempotencyRecord("k", "h");
        finished.complete("ref-1");
        when(records.findById("k")).thenReturn(Optional.of(finished));

        String result =
                service.execute(
                        "k",
                        "h",
                        () -> events.add("hook"),
                        () -> "x",
                        s -> s,
                        ref -> "replayed:" + ref);

        assertThat(result).isEqualTo("replayed:ref-1");
        assertThat(events).isEmpty();
        verifyNoInteractions(transactionTemplate);
    }

    @Test
    void failingHookStopsEverything() {
        when(records.findById("k")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        service.execute(
                                "k",
                                "h",
                                () -> {
                                    throw new LimitsUnavailableException("down");
                                },
                                () -> "x",
                                s -> s,
                                s -> s))
                .isInstanceOf(LimitsUnavailableException.class);

        verifyNoInteractions(transactionTemplate);
        verify(records, never()).saveAndFlush(any());
    }

    @Test
    void theOldFiveArgumentMethodStillWorks() {
        when(records.findById("k")).thenReturn(Optional.empty());
        when(transactionTemplate.<String>execute(any())).thenReturn("done");

        assertThat(service.execute("k", "h", () -> "x", s -> s, s -> s)).isEqualTo("done");
    }
}
