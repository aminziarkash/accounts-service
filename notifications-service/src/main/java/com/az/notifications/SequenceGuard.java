package com.az.notifications;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class SequenceGuard {

    private final JdbcClient jdbc;

    public SequenceGuard(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** True if the sequence is newer than the last one applied for this key (and is now remembered). */
    public boolean advance(String key, long sequence) {
        int changed = jdbc.sql("""
                insert into account_sequences (account_key, last_seq) values (:key, :seq)
                on conflict (account_key) do update set last_seq = excluded.last_seq
                where account_sequences.last_seq < excluded.last_seq
                """)
                .param("key", key)
                .param("seq", sequence)
                .update();
        return changed == 1;
    }
}