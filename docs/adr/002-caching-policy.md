# ADR-002: Caching policy: what is cached and what never is

Status: Accepted

## Context

Reading a customer's name and email is frequent and almost never changes. The same endpoint family also returns
balances, where a stale value is a correctness bug, not just a slow page. We want the speed of a cache for the first
kind of read without ever risking the second.

## Decision

1. **Cache one thing: the customer profile** (`id`, `email`, `firstName`, `lastName`), served by
   `GET /api/v1/customers/{id}/profile`. Accounts, balances and the ledger are never cached. The existing
   `GET /customers/{id}` (with accounts) stays uncached.
2. **Spring's cache abstraction with Caffeine**, in process. The cache is bounded (`app.cache.profile-max-size`,
   default 1000) and entries expire a fixed time after they are written (`app.cache.profile-ttl`, default 5 minutes).
   Expiry after write, not after access, so an entry can never get older than the TTL however often it is read.
3. **The ownership check is never cached.** `AccessPolicy.requireCustomer` runs in the controller on every request,
   before the cached service method. The cache sits behind security, not in place of it.
4. **Cache a small immutable DTO** (`CustomerProfile`), not a JPA entity, so no lazy collections or session state end
   up in the cache.
5. **Evict on change.** `PUT /customers/{id}/profile` changes the name and evicts the entry (`@CacheEvict`), so the next
   read loads fresh data. A failed lookup (customer not found) is not cached.
6. **Hit and miss counts are visible** through Micrometer (`cache_gets_total` with `cache="profiles"`), because
   `recordStats()` is on.

## Alternatives considered

- **Cache balances or the full customer view.** Rejected: a stale balance can show money that is not there. Correctness
  comes before speed for anything that moves money.
- **A shared cache such as Redis.** Gives one consistent copy for all instances, but adds a network hop on every read
  and another service to run and secure. Worth it once stale reads across instances matter; not needed for a name.
- **`@CachePut` instead of evict.** Saves one query after an update, but the cache would then hold a value that did not
  come from a read of the database. Evict keeps the rule simple: the cache only holds what was read from the source.
- **No cache.** Honest and simplest, and fine at today's load. The cache is here to show a safe policy, with limits
  and metrics, that can be extended.

## Consequences

- **Each instance has its own cache.** After a profile change, other instances keep the old value until it expires, at
  most the TTL (5 minutes). Acceptable for a name; this is the reason balances are excluded.
- **A change that bypasses the service** (a script, a manual fix in the database) is also not seen until the entry
  expires or is evicted. The TTL is the safety net.
- **Eviction timing.** `@CacheEvict` runs when the method returns, which can be just before the transaction commits. In
  that small gap another request could read the old row and cache it again, until the TTL ends. Wrapping the cache
  manager in `TransactionAwareCacheManagerProxy` would delay the eviction until after commit. Not done yet.
- **Self-invocation.** `@Cacheable` works through a proxy, so a call from inside the same class skips the cache. Calls
  to `getProfile` must come from another bean (the controller does).
- **Memory is bounded** by the maximum size; the oldest or least used entries are dropped first.

## Evidence

- `CustomerProfileCacheTest`: the second read is a hit and the first a miss, an update evicts, a change behind the
  cache is not seen until eviction (the stale window), and a failed lookup is not cached.
- `CacheSelfInvocationTest`: a call from inside the same class skips the cache.
- `CustomerProfileApiTest`: the profile has no accounts or balances, someone else's profile is `404` even when cached,
  and an update is visible on the next read.
- Manual: the `bruno/caching` folder, with the hit and miss counters in `/actuator/prometheus`.
