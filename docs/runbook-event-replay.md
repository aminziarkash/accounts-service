# Runbook: dead-lettered events and replay

Use this when the alert `DeadLetterTopicNotEmpty` fires, or when a notification is missing and you suspect a
dead-lettered event. Background and the reasons behind the design are in
[ADR-003](adr/003-events-and-messaging.md).

## What a dead letter is

`notifications-service` retries a failing message three times, one second apart, and then publishes a copy to
`accounts.events.DLT` and moves on. Input that can never succeed (`IllegalArgumentException`, for example a missing
`event-id` header) and stale events (`OutOfOrderEventException`) skip the retries and go there at once.

The balance in accounts-service is already correct. What is missing is one notification, until the message is replayed.
Later messages keep flowing.

## 1. See what is in the dead-letter topic

How many messages, per partition (the last number is the end offset):

```bash
docker exec accounts-kafka /opt/kafka/bin/kafka-get-offsets.sh \
  --bootstrap-server localhost:9092 --topic accounts.events.DLT --time -1
```

Read them with their headers:

```bash
docker exec accounts-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic accounts.events.DLT --from-beginning --property print.key=true --property print.headers=true --timeout-ms 5000
```

The `TimeoutException` at the end is only the timeout expiring. It is not an error.

What the headers tell you:

| Header                                 | Meaning                                              | Readable in the console? |
|----------------------------------------|------------------------------------------------------|--------------------------|
| `kafka_dlt-exception-message`          | The error. It also contains `partition N offset M`.  | Yes                      |
| `kafka_dlt-exception-cause-fqcn`       | The real cause (for example `IllegalArgumentException`) | Yes                   |
| `kafka_dlt-original-topic`             | `accounts.events`                                    | Yes                      |
| `kafka_dlt-original-consumer-group`    | `notifications-service`                              | Yes                      |
| `kafka_dlt-original-partition` / `-offset` | Where the message was in the original topic      | **No**, they are binary numbers |

The original partition and offset are stored as raw numbers, so the console prints garbage (offset 76 shows as `L`,
because 76 is the letter L in ASCII). Read them from the text of `kafka_dlt-exception-message` instead, for example
"Missing event-id or event-type header at partition 0 offset 76".

## 2. Find and fix the cause first

Replaying without a fix fails again and adds another copy to the dead-letter topic.

| Cause (`exception-cause-fqcn`)  | Usual reason                                     | Can it be replayed?                                         |
|---------------------------------|--------------------------------------------------|-------------------------------------------------------------|
| `IllegalArgumentException`      | Missing header, bad UUID, bad payload            | Only if the producer is fixed and the event is published again. The stored bytes stay bad. |
| `OutOfOrderEventException`      | An older sequence arrived after a newer one      | No. See "The order guard" below.                            |
| Anything else (database down, bug) | Temporary or a code bug                       | Yes, once the service is healthy or fixed.                  |

## 3. Replay

Replay means moving the consumer group back to an earlier offset. Everything after that offset is delivered again. The
consumer is idempotent: it records every event id in `processed_events`, so events it already handled are skipped.

1. Stop `notifications-service`. Offsets cannot be reset while the group has members.
2. Dry run. It changes nothing and shows the plan:

```bash
docker exec accounts-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notifications-service --topic accounts.events:0 --reset-offsets --to-offset 70 --dry-run
```

3. Run it for real by replacing `--dry-run` with `--execute`.
4. Start `notifications-service` and watch the log.
5. Check the result (below).

Use the partition and offset from the exception message. Rewind to the failed offset, or a little earlier. A reset
applies to the whole partition, so other accounts on that partition are replayed too. They are skipped as duplicates.

## 4. Check the result

- Log: `Duplicate event <id> ignored` for events that were already processed, and the new message handled normally.
- `kafka-consumer-groups.sh --describe --group notifications-service`: lag 0 on every partition.
- `notifications` and `processed_events` grow only by the events that were really missing.
- Dead-letter count: unchanged if the replay worked, +1 if the same message failed again.

## The order guard

The consumer applies an event only if its sequence is higher than the last one it saw for that account. It detects
reordering, not gaps. If event 7 is dead-lettered and event 8 is applied, event 7 can no longer be replayed: it would
be refused as stale. That is acceptable for notifications, because each event carries `balanceAfter`. A read model
would need a gapless version per account instead (apply only last + 1).

## The alert keeps firing

`DeadLetterTopicNotEmpty` compares the newest and oldest offset of the topic, so it stays on until Kafka deletes the
old messages (retention). Replaying does not remove them. Handle each message, note it, and let retention clear it.
On a local machine you can delete and recreate the topic.

## Drill record (2026-11-25, local)

A message without the `event-id` and `event-type` headers was published with key `999`.

- It landed on `accounts.events` partition 0, offset 76. It failed once and went straight to the dead-letter topic,
  with no retries, because `IllegalArgumentException` is not retried.
- The offsets of partition 0 were reset to 70 with a dry run and then `--execute`, and the service was restarted.
- Offsets 70 to 75 (one `TRANSFER_COMPLETED` and five `MONEY_DEPOSITED`) were all logged as
  `Duplicate event ... ignored`. Nothing was applied twice.
- Offset 76 failed again and was dead-lettered again. The dead-letter count on partition 0 went from 1 to 2, which is
  what an unfixed message should do.
