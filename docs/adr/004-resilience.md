# ADR-004: Calling another service: timeouts, retry, circuit breaker, and failing closed

Status: Accepted

## Context

Before a deposit, withdrawal or transfer, accounts-service asks `limits-service` over HTTP whether the amount is
allowed. The call is synchronous: accounts waits for the answer. If limits is slow or down, waiting requests pile up
and accounts becomes slow or stuck even though nothing is wrong with it (a cascading failure). We also have to decide
what a money operation does when no answer comes.

## Decision

1. **The call happens outside the database transaction.** `IdempotencyService.execute` takes a `beforeTransaction`
   hook. It runs after the replay check and before the transaction opens. A slow limits service therefore never holds
   row locks, and a replay of a finished request never asks limits again (the first request was already checked).
2. **Explicit timeouts**: connect 500 ms and read 1 s (`app.limits-client`), on the JDK HTTP client. No call ever
   waits without a limit.
3. **Retry with exponential backoff and jitter**: at most 2 attempts, base wait 100 ms, doubling, randomised by 50%.
   Only "no answer" is retried (timeout, connection refused, 5xx, empty body). The check is a read-only question, so
   repeating it is safe. The money movement itself is never retried by the service; the client retries it with the
   same idempotency key.
4. **Circuit breaker**: counts the last 10 calls, can open after 5, opens at 50% failures, stays open 10 s, then lets
   2 test calls through (half-open). While open, calls fail at once without touching the network. Only "no answer"
   counts as a failure. A denial ("above the limit") is a healthy answer: it is a 200 from limits, a `422` from us.
5. **Retry wraps the breaker** (retry outside, breaker inside). Every attempt counts for the breaker, and once it
   opens the retries stop immediately, so retries cannot pile more load on a service that is already struggling.
6. **Fail closed.** If limits cannot answer (timeout, error or open breaker), the operation is refused with `503` and
   `Retry-After: 5`. Nothing is changed. A money operation is never allowed without the limits check.
7. **Resilience4j, used from code.** The core modules (`circuitbreaker`, `retry`, `micrometer`) are wired by hand in
   `LimitsResilienceConfig` and used through `ResilientLimitsClient`, with all settings under
   `app.limits-client.resilience`. No annotations and no Spring Boot starter.
8. **The breaker is visible**: `resilience4j_circuitbreaker_state` and the call counters in `/actuator/prometheus`,
   and the `LimitsCircuitBreakerOpen` alert (open for 30 s).
9. **An unknown account is a `404` before any remote call**, and `app.limits-client.enabled=false` switches the check
   off for tests that are not about it.

## Alternatives considered

- **Spring Framework 7 core retry** (`@Retryable`, `RetryTemplate`, and `@ConcurrencyLimit`). It needs no extra
  dependency and covers retry with backoff and jitter. It has no circuit breaker, so we would need a second library
  anyway. One library for both keeps the two features consistent, and gives metrics and state transitions for free.
- **Resilience4j annotations** (`@Retry`, `@CircuitBreaker`). Shorter, but they work through a proxy, so a call from
  inside the same class skips them (the same trap as `@Cacheable`), and the order of the two aspects is easy to get
  wrong. Plain `decorateSupplier` calls make the order and the fallback visible in one place.
- **Fail open** (allow when limits cannot answer). Keeps the service available, but the check can then be skipped
  exactly when the system is struggling. Rejected for withdrawals and transfers. Deposits add money, so failing open
  for them can be argued; we did not do it, to keep one simple rule. It is a policy decision for the business.
- **No retry, breaker only.** A single dropped packet would refuse a customer for no reason. Retry absorbs blips, the
  breaker handles real outages; they solve different problems.
- **Check the limit asynchronously** (from events). The limit has to be known before the money moves, so an
  after-the-fact check would only report a breach.
- **Cache or default limits in accounts-service** as a fallback. Stale limits can allow what should be refused;
  not worth the risk for now.

## Consequences

- **Worst case for one request is about 2.1 s** (two attempts of 1 s plus the wait). Once the breaker is open it is a
  few milliseconds.
- **While limits is down, all writes are refused**, including deposits. Reads (balances, profiles) are unaffected.
  Availability of money operations is coupled to limits-service.
- **Each instance has its own breaker.** Instances open and close independently, and the state is lost on restart.
- **The numbers are educated guesses** (window, threshold, 10 s). They need a load test and real traffic to tune.
- **Our own 5xx counts as a failure; a 4xx does not** (it would be our bug, and shows up as a `500`).
- **No authentication between the two services yet.** limits-service is open on its port. In production it needs
  mutual TLS or a service token.
- **The `limits-service` chaos switch** (`/admin/chaos`) exists only for demos and failure tests and must be off in any
  real environment (`app.chaos.enabled=false`).

## Evidence

- `LimitsClientTest` (WireMock): allowed, denied, 500, empty body, a slow answer cut off by the read timeout,
  and nobody listening.
- `LimitsResilienceTest` (WireMock, real timing): a bad answer is retried, retry gives up after the maximum,
  a denial is neither retried nor a failure, the breaker opens and then refuses without network calls, retries stop
  when it opens, it closes after successful test calls and opens again if they fail.
- `IdempotencyBeforeTransactionTest`: the hook runs before the transaction, never for a replay, and a failing hook
  opens no transaction and stores nothing.
- `LimitsGuardTest` and `LimitsErrorMappingApiTest`: the guard rules, `503` with `Retry-After`, and `422` for a denial.
- Manual: the `bruno/resilience` folder plays the whole story (durations drop from about 2 s to a few ms, then the
  breaker closes again), and the `LimitsCircuitBreakerOpen` alert in Prometheus.
