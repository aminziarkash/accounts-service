package com.az.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EventPayloadTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private static final String V1 =
            """
            {"accountId":3,"ledgerEntryId":5,"amount":100.00,"currency":"USD",\
            "balanceAfter":100.00,"occurredAt":"2026-11-15T15:26:30Z"}""";

    private static final String V2 =
            """
            {"accountId":3,"ledgerEntryId":5,"amount":100.00,"currency":"USD",\
            "balanceAfter":100.00,"occurredAt":"2026-11-15T15:26:30Z",\
            "correlationId":"req-42","schemaVersion":2}""";

    /** What the consumer looked like before v2: it knows only the v1 fields. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OldEventPayload(Long accountId, BigDecimal amount, String currency, BigDecimal balanceAfter) {}

    @Test
    void newConsumerReadsAnOldEventWhereTheNewFieldsAreMissing() throws Exception {
        EventPayload event = mapper.readValue(V1, EventPayload.class);

        assertThat(event.accountId()).isEqualTo(3L);
        assertThat(event.balanceAfter()).isEqualByComparingTo("100.00");
        assertThat(event.correlationId()).isNull();
        assertThat(event.schemaVersion()).isNull(); // absent means version 1
    }

    @Test
    void newConsumerReadsTheNewFields() throws Exception {
        EventPayload event = mapper.readValue(V2, EventPayload.class);

        assertThat(event.correlationId()).isEqualTo("req-42");
        assertThat(event.schemaVersion()).isEqualTo(2);
    }

    @Test
    void oldConsumerStillReadsANewEvent() throws Exception {
        OldEventPayload event = mapper.readValue(V2, OldEventPayload.class);

        assertThat(event.accountId()).isEqualTo(3L);
        assertThat(event.amount()).isEqualByComparingTo("100.00");
        assertThat(event.currency()).isEqualTo("USD");
        assertThat(event.balanceAfter()).isEqualByComparingTo("100.00");
    }

    @Test
    void aNullCorrelationIdIsFine() throws Exception {
        String json = V2.replace("\"correlationId\":\"req-42\"", "\"correlationId\":null");

        EventPayload event = mapper.readValue(json, EventPayload.class);

        assertThat(event.correlationId()).isNull();
        assertThat(event.schemaVersion()).isEqualTo(2);
    }
}