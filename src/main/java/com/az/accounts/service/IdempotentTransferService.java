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
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IdempotentTransferService {

    private final AccountService accountService;
    private final IdempotencyRecordRepository records;
    private final TransactionTemplate transactionTemplate;

    public IdempotentTransferService(
            AccountService accountService,
            IdempotencyRecordRepository records,
            TransactionTemplate transactionTemplate) {
        this.accountService = accountService;
        this.records = records;
        this.transactionTemplate = transactionTemplate;
    }

    public UUID transfer(String key, Long fromId, Long toId, BigDecimal amount) {
        String hash = requestHash(fromId, toId, amount);

        Optional<IdempotencyRecord> existing = records.findById(key);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }

        try {
            return transactionTemplate.execute(
                    status -> {
                        // insert the key first: a concurrent duplicate waits here and then fails
                        IdempotencyRecord record =
                                records.saveAndFlush(new IdempotencyRecord(key, hash));
                        UUID reference = accountService.transfer(fromId, toId, amount);
                        record.complete(reference);
                        return reference;
                    });
        } catch (DataIntegrityViolationException e) {
            // someone with the same key committed first: return their result
            IdempotencyRecord winner = records.findById(key).orElseThrow(() -> e);
            return replay(winner, hash);
        }
    }

    private UUID replay(IdempotencyRecord record, String hash) {
        if (!record.getRequestHash().equals(hash)) {
            throw new IdempotencyKeyReuseException(record.getIdempotencyKey());
        }
        return record.getTransferReference();
    }

    private static String requestHash(Long fromId, Long toId, BigDecimal amount) {
        // 10.0 and 10.00 are the same request
        String canonical = fromId + ":" + toId + ":" + amount.stripTrailingZeros().toPlainString();
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}