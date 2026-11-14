package com.az.accounts.service;

import com.az.accounts.domain.IdempotencyRecord;
import com.az.accounts.exception.IdempotencyKeyReuseException;
import com.az.accounts.repository.IdempotencyRecordRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs an operation at most once per idempotency key. The key row is written in the same
 * transaction as the operation, and the primary key settles races between duplicates.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository records;
    private final TransactionTemplate transactionTemplate;

    public IdempotencyService(
            IdempotencyRecordRepository records, TransactionTemplate transactionTemplate) {
        this.records = records;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * @param requestHash identifies the request, see {@link #hash}
     * @param action the real work, runs inside the transaction that also stores the key
     * @param toRef turns the result into a short reference that is stored with the key
     * @param fromRef turns a stored reference back into the result, used for replays
     */
    public <T> T execute(
            String key,
            String requestHash,
            Supplier<T> action,
            Function<T, String> toRef,
            Function<String, T> fromRef) {

        Optional<IdempotencyRecord> existing = records.findById(key);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash, fromRef);
        }

        try {
            return transactionTemplate.execute(status -> {
                // insert the key first: a concurrent duplicate waits here and then fails
                IdempotencyRecord record =
                        records.saveAndFlush(new IdempotencyRecord(key, requestHash));
                T result = action.get();
                record.complete(toRef.apply(result));
                return result;
            });
        } catch (DataIntegrityViolationException e) {
            // someone with the same key committed first: return their result
            IdempotencyRecord winner = records.findById(key).orElseThrow(() -> e);
            return replay(winner, requestHash, fromRef);
        }
    }

    private <T> T replay(
            IdempotencyRecord record, String requestHash, Function<String, T> fromRef) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReuseException(record.getIdempotencyKey());
        }
        return fromRef.apply(record.getResultRef());
    }

    /**
     * Fingerprint of a request. The operation name is part of it, so the same key cannot be reused
     * for a deposit and a withdrawal. 10.0 and 10.00 count as the same amount.
     */
    public static String hash(String operation, Object... parts) {
        String canonical =
                Stream.concat(Stream.of(operation), Stream.of(parts))
                        .map(IdempotencyService::canonical)
                        .collect(Collectors.joining(":"));
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String canonical(Object part) {
        if (part instanceof BigDecimal amount) {
            return amount.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(part);
    }
}
