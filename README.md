# accounts-service

Banking accounts service built with Spring Boot 4, PostgreSQL and Testcontainers: ledger, safe money movement under
concurrency, a validated REST API and idempotent money operations. A transactional outbox with Kafka, JWT security and caching
are planned.

![CI](https://github.com/aminziarkash/accounts-service/actions/workflows/ci.yml/badge.svg)

I built this to practice the problems that come up in real backend work: moving money correctly under concurrency,
publishing events reliably, and proving behavior with tests against real infrastructure. Everything in here is code I
wrote and can explain.

## What it shows

Built:

- **Correct money movement.** Deposit, withdraw and transfer with a ledger entry for every balance change, saved in the
  same transaction. Pessimistic locking with ordered locks for balances, optimistic locking for profiles. See ADR-001.
- **Real tests.** PostgreSQL runs in Testcontainers, no H2. Concurrency tests prove there are no lost updates or
  deadlocks.
- **A clean REST API.** Thin controllers, Bean Validation and one error format (RFC 9457 `ProblemDetail`). Services
  return read models mapped with MapStruct, so entities never reach the web layer. The ledger endpoint caps the page
  size and only sorts by whitelisted fields.
- **Safe retries.** Deposits, withdrawals and transfers need an `Idempotency-Key`. The key is stored in PostgreSQL in the same transaction as the
  transfer, so a repeated request returns the first result and money moves once, even for concurrent duplicates. Keys expire
  after 24 hours and a scheduled job deletes them.
- **Cross-cutting basics.** A correlation id filter, request timing interceptor, configurable CORS and an `@Audited`
  aspect for money operations.
- **Documented API.** OpenAPI docs and Swagger UI generated with springdoc.

Planned:

- **Reliable events.** A transactional outbox publishing domain events to Kafka, with idempotent consumers and a
  dead-letter topic.
- **Secure by default.** A stateless JWT resource server with ownership checks.

## Stack

| Area                 | Technology                                                                                        |
|----------------------|---------------------------------------------------------------------------------------------------|
| Language and runtime | Java 21                                                                                           |
| Framework            | Spring Boot 4.1 (Spring Framework 7), Spring MVC (servlet stack)                                  |
| API                  | REST and JSON, versioned under `/api/v1`, Bean Validation, RFC 9457 `ProblemDetail` errors        |
| API docs             | OpenAPI and Swagger UI with springdoc                                                             |
| Cross-cutting        | Servlet filter (correlation id), `HandlerInterceptor` (timing), Spring AOP (audit), CORS config   |
| Persistence          | Spring Data JPA, Hibernate 7, PostgreSQL 16                                                       |
| Schema migrations    | Liquibase (formatted SQL)                                                                         |
| Mapping              | MapStruct (compile-time, no reflection)                                                           |
| Concurrency          | Pessimistic locking with ordered locks for balances, optimistic locking (`@Version`) for profiles |
| Testing              | JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers (real PostgreSQL, no H2), ArchUnit                |
| Build and quality    | Maven wrapper, Spotless (google-java-format), GitHub Actions CI                                   |
| Local tooling        | Docker Compose, Keycloak, Dockerfile, Bruno collection in `bruno/`                                |

**Planned:** JWT resource server (Spring Security), Caffeine caching, Kafka with a transactional outbox, Resilience4j,
Micrometer and Prometheus.

## Run it

```bash
docker compose up -d                                          # PostgreSQL on 5433, Keycloak on 8180
./mvnw spring-boot:run -Dspring-boot.run.profiles=local       # API on http://localhost:8080
./mvnw verify                                                 # unit and integration tests
```

Try the API with the [Bruno](https://www.usebruno.com/) collection in `bruno/`: open the folder as a collection and
select the `local` environment. Run one of the requests in the `auth` folder first (Get token for alice, bob or admin).
It stores the access token in memory and every other request sends it as a bearer token. Tokens are never written to
a file.

### Identity provider

Keycloak runs in Docker Compose and imports the `accounts` realm from `keycloak/accounts-realm.json`: roles `USER` and
`ADMIN`, users `alice`, `bob` (USER) and `admin` (USER and ADMIN) with the username as the password, and a public client
`accounts-web` for the future React app (Authorization Code with PKCE). These are local development credentials only.
Admin console: http://localhost:8180 (admin / admin).

API docs: http://localhost:8080/swagger-ui.html (OpenAPI JSON at `/v3/api-docs`).

Health probes for containers and Kubernetes: `/actuator/health/liveness` (is the process alive) and
`/actuator/health/readiness` (can it serve traffic; includes the database). Shutdown is graceful: in-flight requests
get up to 20 seconds to finish.

### Run it in Docker

```bash
docker compose up -d
docker build -t accounts-service .
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/accounts \
  -e SPRING_DATASOURCE_USERNAME=accounts -e SPRING_DATASOURCE_PASSWORD=accounts \
  -e KEYCLOAK_JWK_SET_URI=http://host.docker.internal:8180/realms/accounts/protocol/openid-connect/certs \
  accounts-service
```

The image is built in two stages (JDK to build, JRE to run) and runs as a non-root user. Configuration comes from
environment variables, nothing is baked into the image.

## Architecture

Layered Spring Boot service: controllers (`api`) call services (`service`), which use repositories (`repository`) and
the domain model (`domain`). Entities never leave the service layer: services map them to read models
(`service.view`) inside the transaction, so controllers cannot trigger lazy loading. Business exceptions live with the
domain and are translated to HTTP in one `@RestControllerAdvice`. A servlet filter adds a correlation id to every
request, and deposits, withdrawals and transfers are made idempotent with one key table, written in the same
transaction as the money movement.
The service is an OAuth2 resource server: every `/api/**` call needs a bearer token signed by Keycloak. Health probes
and the API docs are public, everything else is denied by default. Realm roles in the token become Spring roles, and
401 and 403 answers use the same problem+json format as every other error.
A valid token only proves who you are, so one `AccessPolicy` also checks that the customer or account is yours (admins
may see everything). Each customer stores the Keycloak id of its owner, taken from the token and never from the request.
Someone else's customer or account answers `404`, the same as an id that does not exist, so ids cannot be probed.
You can send money to anyone's account, but only out of your own.
The layer rules are enforced by ArchUnit tests, so the build fails if a controller touches an entity or a repository.

[Add the diagram here once built: a Mermaid diagram of accounts-service, PostgreSQL, outbox relay, Kafka, notifications-service.]

## Decisions

| ADR                                     | Decision                                                  | Status   |
|-----------------------------------------|-----------------------------------------------------------|----------|
| [001](docs/adr/001-locking-strategy.md) | Pessimistic locking for balances, optimistic for profiles | Accepted |
| 002                                     | Caching policy: what is cached and what never is          | Planned  |
| 003                                     | Event model and messaging style                           | Planned  |
| 004                                     | Resilience4j versus Spring Framework 7 core retry         | Planned  |

## Roadmap

- [x] Foundation, CI
- [x] Persistence and transactions
- [x] REST API (validation, error handling, idempotent deposits, withdrawals and transfers, OpenAPI)
- [ ] Security (JWT)
- [ ] Caching
- [ ] Event-driven core (outbox, Kafka, idempotent consumer)
- [ ] Failure handling, schema evolution, read model
- [ ] Observability and delivery

## Known limits

- Idempotency keys are global. With security, they will be stored per caller (`client id + key`), so one user can
  never replay another user's key.
- A key is forgotten after 24 hours; a retry after that counts as a new request.
- A busy account answers `503` with `Retry-After` after waiting 3 seconds for its row lock.

## Out of scope

A real OAuth2 authorization server, Kubernetes manifests, and a UI.

## License

MIT
