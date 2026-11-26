package com.az.accounts.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON of a MoneyDeposited is a contract. These fields may be added to, never renamed or
 * removed.
 */
class MoneyDepositedSchemaTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private JsonNode json(String correlationId) {
        MoneyDeposited event =
                new MoneyDeposited(
                        3L,
                        5L,
                        new BigDecimal("100.00"),
                        "USD",
                        new BigDecimal("100.00"),
                        Instant.parse("2026-11-15T15:26:30Z"),
                        correlationId,
                        2);
        return mapper.readTree(mapper.writeValueAsString(event));
    }

    @Test
    void theVersion1FieldsAreStillThereWithTheSameNamesAndTypes() {
        JsonNode node = json("req-42");

        assertThat(node.get("accountId").longValue()).isEqualTo(3L);
        assertThat(node.get("ledgerEntryId").longValue()).isEqualTo(5L);
        assertThat(node.get("amount").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(node.get("currency").textValue()).isEqualTo("USD");
        assertThat(node.get("balanceAfter").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(node.get("occurredAt").textValue()).isEqualTo("2026-11-15T15:26:30Z");
    }

    @Test
    void theNewFieldsAreAdded() {
        JsonNode node = json("req-42");

        assertThat(node.get("correlationId").textValue()).isEqualTo("req-42");
        assertThat(node.get("schemaVersion").intValue()).isEqualTo(2);
    }

    @Test
    void aMissingCorrelationIdStillProducesAValidEvent() {
        JsonNode node = json(null);

        assertThat(node.has("correlationId")).isTrue();
        assertThat(node.get("correlationId").isNull()).isTrue();
        assertThat(node.get("schemaVersion").intValue()).isEqualTo(2);
    }
}
