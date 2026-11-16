# ADR-005: Observability: what we monitor and why

Status: Accepted

## Context

The service now publishes events through an outbox and a relay, and a second service consumes them. Failures here are
quiet: a deposit still succeeds while its event waits in a table, or a consumer stops and nobody notices. We need to
see problems before a user reports them, and we need alerts that point at user impact, not at every CPU spike.

## Decision

1. **Metrics through Micrometer, exposed at `/actuator/prometheus`** in both services, tagged with the application
   name. Prometheus **pulls** (scrapes) every 15 seconds, so a dead service shows up as a failed scrape (`up == 0`).
2. **Grafana for dashboards**, provisioned from files in the repository (data source and dashboard), so the setup is
   reproducible and reviewed like code.
3. **Consumer lag comes from a Kafka exporter**, not from the applications. The exporter compares the broker's newest
   offsets with each group's committed offsets (`kafka_consumergroup_lag`). The application cannot see its own lag
   when it is stuck.
4. **Custom business metrics** for the event pipeline:
   - accounts-service: `outbox_events_pending`, `outbox_oldest_pending_age_seconds` (gauges),
     `outbox_events_published_total`, `outbox_publish_failures_total` (counters).
   - notifications-service: `notifications_events_processed_total` (tag `type`), `notifications_events_duplicates_total`,
     `notifications_events_skipped_total`, `notifications_events_dead_lettered_total` (tag `reason`).
5. **Seven alert rules** in `monitoring/prometheus/alerts.yml`, written against symptoms:

   | Alert | Fires when | Severity |
   |---|---|---|
   | `OutboxEventStuck` | the oldest unpublished event is older than 60 s for 2 min | page |
   | `OutboxPublishFailing` | any publish failure in the last 5 min | warn |
   | `ConsumerLagHigh` | lag above 100 for 5 min | page |
   | `ConsumerGroupHasNoMembers` | no notifications-service consumer for 2 min | page |
   | `DeadLetterTopicNotEmpty` | there are messages in `accounts.events.DLT` | warn |
   | `ServiceDown` | a target cannot be scraped for 1 min | page |
   | `HighServerErrorRate` | more than 5% of requests return 5xx for 5 min | page |

6. **Tags have a small, fixed set of values** (event type, failure reason). Never an account id or event id: every
   distinct tag value is a separate time series, and unbounded values overload Prometheus.

## Alternatives considered

- **Push metrics to a collector.** Services would need to know where the monitoring is, and a dead service would just go
  quiet instead of showing as down. Pull suits long-running services; push suits short-lived jobs.
- **Alert on the pending count.** A count can look fine while one row ages for an hour. The age of the oldest event
  always grows when the pipeline is stuck, so we alert on that and show the count on the dashboard.
- **Application-side lag metrics.** A stuck or dead consumer cannot report its own lag. The exporter measures from
  outside.
- **A hosted monitoring product.** Good for production, but it hides the mechanics. The local stack is open source and
  runs with `docker compose up`.

## Consequences

- Window-based alerts such as `OutboxPublishFailing` stay red until the last failure leaves the 5 minute window, even
  after the broker is back. The age-based alert is the one that follows real recovery.
- `kafka_consumergroup_lag` reports `-1` for a partition where the group has no committed offset yet. Queries must
  filter with `>= 0` or use `max`, and a group that never consumed hides its backlog, which is why there is also an
  alert on a group with no members.
- There is no Alertmanager locally, so alerts are only visible in the Prometheus UI. In production Alertmanager routes
  them by severity: a page wakes someone (PagerDuty), a warning goes to a chat channel.
- `/actuator/prometheus` is public in the API so Prometheus can scrape it. In production it must only be reachable
  inside the cluster network or on a separate management port.
- Not covered yet: distributed tracing (OpenTelemetry), log aggregation, and service level objectives with burn-rate
  alerts.

## Evidence

- `OutboxMetricsTest` and `OutboxPublisherTest` check that the gauges and counters change as events are recorded,
  published and fail.
- Manual check: stop Kafka, make deposits, and watch `outbox_events_pending` and the oldest-event age rise and the
  alerts fire; start Kafka and watch both return to zero.
