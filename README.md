# accounts-service

Event-driven banking accounts service built with Spring Boot 4, PostgreSQL, Kafka and Testcontainers: ledger, idempotent transfers, outbox, JWT security and caching

![CI](https://github.com/<your-username>/accounts-service/actions/workflows/ci.yml/badge.svg)

I built this to practice the problems that come up in real backend work: moving money correctly under concurrency, publishing events reliably, and proving behavior with tests against real infrastructure. Everything in here is code I wrote and can explain.

## What it shows

- **Correct money movement.** Deposit, withdraw and transfer with a ledger entry for every balance change. Pessimistic locking with ordered locks for transfers, optimistic locking for profiles. See ADR-001.
- **Safe retries.** Transfers accept an `Idempotency-Key`, so a repeated request moves money once.
- **Reliable events.** A transactional outbox publishes domain events to Kafka. Consumers are idempotent, failures go to a dead-letter topic. See ADR-003.
- **Real tests.** PostgreSQL and Kafka run in Testcontainers, no H2. Concurrency tests prove there are no lost updates or deadlocks.
- **Secure by default.** Stateless JWT resource server with ownership checks.

## Stack

Java 21, Spring Boot 4, Spring Data JPA, PostgreSQL 16, Liquibase, Kafka, Caffeine, Resilience4j, Testcontainers, Docker, GitHub Actions.

## Run it

```bash
docker compose up -d        # PostgreSQL (and Kafka once added)
./mvnw spring-boot:run
./mvnw verify               # unit and integration tests
```

API docs: http://localhost:8080/swagger-ui.html (once phase 4 is done)

## Architecture

[Add the diagram here once built: a Mermaid diagram of accounts-service, PostgreSQL, outbox relay, Kafka, notifications-service.]

## Decisions

| ADR | Decision |
|-----|----------|
| 001 | Pessimistic locking for transfers, optimistic for profiles |
| 002 | Caching policy: what is cached and what never is |
| 003 | Event model and messaging style |
| 004 | Resilience4j versus Spring Framework 7 core retry |

## Roadmap

- [x] Foundation, CI
- [ ] Persistence and transactions
- [ ] REST API and security
- [ ] Caching
- [ ] Event-driven core (outbox, Kafka, idempotent consumer)
- [ ] Failure handling, schema evolution, read model
- [ ] Observability and delivery

## Out of scope

A real OAuth2 authorization server, Kubernetes manifests, and a UI.

## License

MIT
