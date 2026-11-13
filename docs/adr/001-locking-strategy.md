# ADR-001: Locking strategy for concurrent updates

Status: Accepted

## Context

Two requests can change the same data at the same time. Without protection,
the last writer silently wins and the other change is lost (a lost update).

We have two different kinds of data:

- **Account balances.** Updated often, by deposits, withdrawals and transfers.
  A lost update here means wrong money.
- **Customer profiles.** Updated rarely, by one person at a time. A conflict
  means two browser tabs saved different edits.

## Decision

1. **Account rows: pessimistic locking.** `deposit`, `withdraw` and `transfer`
   load the account with `SELECT ... FOR UPDATE`
   (`@Lock(PESSIMISTIC_WRITE)`, `AccountRepository.findByIdForUpdate`).
   Other writers wait until the transaction ends.
2. **Transfers lock the lower account id first.** Both accounts are always
   locked in the same order, so two opposite transfers (A to B and B to A)
   cannot wait on each other and deadlock.
3. **Customer rows: optimistic locking.** `Customer` has a `@Version`
   column. An update with a stale version fails with
   `ObjectOptimisticLockingFailureException`, which the API will return as
   `409 Conflict`.

## Alternatives considered

- **Optimistic locking for balances.** No blocking, but under contention many
  requests would fail and need retries. Every caller would have to handle
  retries correctly for money movements. Rejected for balances.
- **Pessimistic locking for profiles.** Safe, but it holds a database lock for
  data that almost never conflicts. Rejected as wasted blocking.
- **SERIALIZABLE isolation everywhere.** Simple to reason about, but it costs
  throughput and still needs retries on serialization failures.
- **A single global lock or a queue per account.** Hard to scale and adds
  infrastructure we do not need yet.

## Consequences

- Balance updates on the same account are serialized, so a very hot account is
  a throughput limit. Acceptable for this service. If it becomes a problem,
  we can look at partitioning or a ledger-only write path.
- Locks are held for the whole transaction, so transactions that touch
  accounts must stay short. No remote calls inside them.
- Consistent lock ordering is a rule every new multi-account operation must
  follow. It is covered by `opposite transfers do not deadlock`.
- Clients must handle `409 Conflict` on profile updates by reloading and
  retrying.

## Evidence

- `AccountServiceTest`: concurrent deposits keep the balance and ledger
  consistent; opposite transfers do not deadlock.
- `CustomerOptimisticLockingTest`: a stale customer update is rejected.