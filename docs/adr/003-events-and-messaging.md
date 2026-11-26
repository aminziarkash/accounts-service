# ADR-003: Events and messaging: outbox, Kafka, idempotent consumer, dead-letter topic

Status: Accepted

## Context

When money moves, other systems must hear about it (today: notifications-service). Writing to the database and
publishing to Kafka are two systems, and no transaction spans both. If we commit and then publish, a crash between the
two loses the event. If we publish and then commit, a rollback leaves an event for something that never happened.
We also have to decide what a consumer does with a duplicate, an out-of-order event and a message it cannot process.

## Decision

1. **Transactional outbox.** The event is written to `outbox_events` in the same transaction as the money movement
   (`OutboxService`). Either both are saved or neither is. Nothing is sent to Kafka from inside the request.
2. **A relay publishes the outbox.** `OutboxRelay` polls every 0.5 s. `OutboxPublisher.publishBatch` locks up to 50
   unpublished rows with `FOR UPDATE SKIP LOCKED`, sends them in id order, waits for the broker acknowledgement
   (5 s timeout), then marks each row published. It stops at the first failure so the order is kept and the row is
   retried on the next poll. **Only one relay works at a time**: each batch starts with a Postgres advisory lock
   (`pg_try_advisory_xact_lock`). A relay that does not get the lock returns at once and tries again at the next poll.
   The lock belongs to the transaction, so it is released at commit, rollback or a crashed connection.
3. **Delivery is at least once.** A crash after the broker acknowledged but before the row is marked published sends
   that event again. We accept duplicates and make the consumer safe, instead of trying for exactly once.
4. **Producer settings:** `acks=all`, `enable.idempotence=true` (no duplicates from producer retries), and
   `max.block.ms=5000` (a dead broker must not block a send for the 60 s default).
5. **Message key = account id.** Kafka keeps order inside a partition, and one key always goes to one partition, so
   events of one account are consumed in the order they were written. There is no order between different accounts.
   A transfer is one event, keyed by the source account.
   The topic `accounts.events` has 3 partitions (checked with `kafka-topics.sh --describe`), and so has the
   dead-letter topic. So at most 3 instances of a consumer group work in parallel; a fourth would sit idle. The partition
   count is hard to change later, because changing it changes which partition a key maps to and breaks the order of
   keys already in the topic.
6. **Headers carry `event-id`, `event-type` and `event-seq`**, so the consumer can deduplicate, route and check order
   without parsing the body. `event-seq` is the outbox row id: it grows with every row, and events of one account are
   written in order because the account row lock serializes them.
7. **Idempotent consumer.** `NotificationService.handle` inserts the `event-id` into a processed-events table in the
   same transaction as the side effect. The unique constraint rejects a second insert, and the duplicate changes
   nothing.
   **Order guard:** after the duplicate check, one upsert on `account_sequences` (one row per message key) only
   succeeds if `event-seq` is higher than the last one applied for that key. If it is not, the consumer throws
   `OutOfOrderEventException`: the transaction rolls back (the event is not marked as processed), the error handler does
   not retry it (retrying a stale event never fixes it), and it goes to the dead-letter topic where someone sees it.
   An event without an `event-seq` header (older events) is accepted without the check.
8. **Retries, then a dead-letter topic.** `DefaultErrorHandler` retries 3 times, 1 s apart, then
   `DeadLetterPublishingRecoverer` publishes to `accounts.events.DLT` (partition chosen by the producer, so the DLT does
   not need the same partition count) and the consumer moves on. A missing header or a bad UUID
   (`IllegalArgumentException`) is not retried, because it fails the same way every time.
9. **Offsets use the container default:** the offset is committed after the listener method returns without an
   exception. We do not ack manually. The side effect and the processed-events row are already one database
   transaction, so a redelivery after a crash is harmless. Manual acks would matter only if we processed on another
   thread.
10. **Consumer group `notifications-service`** with `auto-offset-reset=earliest`: a new group reads the topic from the
    start.

## Alternatives considered

- **Publish to Kafka inside the request, after commit.** Simple, but a crash between commit and send loses the event
  silently. The outbox makes that impossible at the cost of a small delay.
- **Kafka transactions / exactly once across database and broker.** Kafka transactions cover Kafka to Kafka, not a
  PostgreSQL commit. At least once plus an idempotent consumer gives the same visible result with less machinery.
- **Change data capture (Debezium) instead of polling.** No polling delay and no relay code, but it needs Kafka
  Connect to run and operate. Polling is enough for this load; CDC is the upgrade path.
- **`@TransactionalEventListener(AFTER_COMMIT)` to publish.** Same lost-event gap as publishing after commit. Fine for
  in-process side effects, not a replacement for the outbox.
- **Deduplicate by offset instead of event id.** Offsets change if the topic is rebuilt or replayed. The event id
  belongs to the event.
- **Retry forever on a bad message.** One poison message would block its whole partition. The dead-letter topic keeps
  the rest of the stream moving.

## Consequences

- **Events can be delayed** by up to the poll interval, and longer while Kafka is down. The outbox grows and the
  `OutboxPublishFailing` alert and the pending-rows metric show it.
- **Duplicates reach the consumer.** Every consumer must be idempotent. This is a rule for every new consumer.
- **Order per account holds, with a price.** Rows go out in id order and only one relay publishes at a time, so
  events of one account reach Kafka in the order they were written. Events of one account are written in order
  because the account row lock serializes them. Before the advisory lock, two relays could swap two events: with
  `SKIP LOCKED`, relay B could publish rows 51 to 100 while relay A still held rows 1 to 50. The price is that publishing
  speed is limited to one relay. If that is ever too slow, give each relay a fixed set of accounts (account id modulo the
  number of relays) instead of one global lock.
- **The consumer refuses reordering, but not gaps.** Sequence numbers are not consecutive per account (the outbox id is
  shared by all accounts), so an event that never arrives is not detected. Detecting gaps would need a per-account
  counter. Refusing a stale event is the right choice for a consumer that must apply every fact in order. A consumer
  that only keeps the latest state (each event carries the full balance) could skip a stale event and count it instead.
- **A refused event is not replayed as it is.** By the time someone looks at it, newer events were applied, so replaying
  the same message is refused again. Recovery is a decision: accept that the older fact is superseded, or rebuild the
  affected account by resetting the consumer group offset (runbook still to be written).
- **The dead-letter topic and the order guard work against each other.** If event 7 of an account is dead-lettered (for
  example a processing error after the retries) and event 8 is applied, the guard remembers 8, so a replay of 7 is
  refused as old. For a consumer that keeps state from the event (a read model that adds deposit amounts), that leaves the
  state wrong. Such a consumer needs a gapless per-account version (a version column on the account row, not the shared
  outbox id), applies only `last + 1`, and holds later events until the gap closes. Our notifications consumer does not
  keep a balance, and deposit and withdrawal events carry `balanceAfter`, so a late event is superseded and the
  current guard is enough for it. Any future read model must use the gapless approach. (Raised by a reader on LinkedIn.)
- **A dead-lettered message loses its place in the order.** Later events for the same key are processed while it
  waits. Whether that is acceptable depends on the data; for money it needs an ordered recovery process.
- **Published outbox rows are never deleted yet.** The table only grows. A cleanup job is needed.
- **Dead-lettered messages are not replayed automatically.** Someone must look at them, fix the cause, and replay
  (the runbook is still to be written). Replay is safe only because consumers are idempotent.
- **The event schema has one version.** Changing a field needs a plan (add optional fields, never rename or remove, or
  add a version header) before another consumer exists.
- **No authentication on the local broker** (PLAINTEXT listeners). In production: TLS and SASL.

## Evidence

- `OutboxIntegrationTest`: the event is stored with the money movement and published to a real Kafka (Testcontainers).
- `OutboxPublisherTest`: order is kept, publishing stops at the first failure, the failed row is retried.
- `OutboxOrderingTest`: two relays and two events of one account. The first relay is held while it owns row 1; the
  second runs in the meantime. Before the advisory lock the test failed (20.00 reached Kafka before 10.00). Now it passes.
- `OutboxMetricsTest`: pending and failure metrics.
- `NotificationServiceTest`: a newer event is applied, an older one is refused and not marked as processed, accounts
  have their own order, a redelivered old event is ignored (not refused), and an event without `event-seq` is accepted.
- `NotificationServiceTest` and `AccountEventsListenerIntegrationTest`: a duplicate changes nothing, headers are
  required, the event produces one notification.
- `DeadLetterIntegrationTest`: a bad message is retried, then lands in the DLT, and the next message still works.
