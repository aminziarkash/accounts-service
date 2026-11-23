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
   retried on the next poll.
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
6. **Headers carry `event-id` and `event-type`**, so the consumer can deduplicate and route without parsing the body.
7. **Idempotent consumer.** `NotificationService.handle` inserts the `event-id` into a processed-events table in the
   same transaction as the side effect. The unique constraint rejects a second insert, and the duplicate changes
   nothing.
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
- **Order per account holds with one relay instance.** With several instances, `SKIP LOCKED` lets instance B publish
  rows 51 to 100 while instance A still holds rows 1 to 50, so two events of the same account can swap places. If order
  matters across instances: run one active relay (a lock or ShedLock), or give each relay a fixed set of accounts
  (id modulo the instance count). Not solved yet; see the ordering test in the roadmap.
- **Published outbox rows are never deleted yet.** The table only grows. A cleanup job is needed.
- **Dead-lettered messages are not replayed automatically.** Someone must look at them, fix the cause, and replay
  (the runbook is still to be written). Replay is safe only because consumers are idempotent.
- **The event schema has one version.** Changing a field needs a plan (add optional fields, never rename or remove, or
  add a version header) before another consumer exists.
- **No authentication on the local broker** (PLAINTEXT listeners). In production: TLS and SASL.

## Evidence

- `OutboxIntegrationTest`: the event is stored with the money movement and published to a real Kafka (Testcontainers).
- `OutboxPublisherTest`: order is kept, publishing stops at the first failure, the failed row is retried.
- `OutboxMetricsTest`: pending and failure metrics.
- `NotificationServiceTest` and `AccountEventsListenerIntegrationTest`: a duplicate changes nothing, headers are
  required, the event produces one notification.
- `DeadLetterIntegrationTest`: a bad message is retried, then lands in the DLT, and the next message still works.
