# NotificationContext FIFO delivery design

## Decision

Repair `coalescingNotificationContext` in place. Keep its public signature and
name: it still coalesces a burst into one active platform drain, never drops
callbacks, and retains synchronous delivery for an idle target-thread post.
Do not move serialization into `ConcurrentStore`; `NotificationContext` is the
existing boundary shared by all notification users and implementations.

The helper owns an `ArrayDeque` behind a `SynchronizedObject` and one drain
claim. `post` is the linearization point: it appends to the FIFO queue while
holding that lock. Concurrent producers have no externally observable order
beyond this synchronization point. Only the producer that acquires an idle
claim starts delivery. It drains inline only when it is already on the target
thread and no older work was queued or draining; otherwise it requests one
platform callback. A target-thread post behind an existing claim only appends.

The drain removes and invokes callbacks outside the lock, one at a time. A
reentrant post observes the claim and appends after the current callback, so it
cannot recurse. Each drain executes at most 64 callbacks, then schedules a
continuation if work remains. This yields to normal UI-loop work under a
sustained producer while keeping the active claim across the handoff and
therefore preventing a second drainer.

The existing `post: (() -> Unit) -> Unit` hook has no rejection result. Its
contract is therefore "schedule or throw": an adapter with an acceptance
result, such as Android `Handler.post`, must throw when rejected. Any exception
from target detection or scheduling releases the claim before it propagates, so
later work can retry the queued FIFO. Callback exceptions are remembered,
delivery continues through the bounded batch, the next drain is arranged when
needed, and the first exception is rethrown only after that cleanup. This is
defensive for general contexts; `ConcurrentStore` already isolates subscriber
errors through `onError`.

`SynchronizedObject` supplies the publication edge for queue operations and
claim transitions, which is available in common code on JVM, Android, Native,
JS, and wasmJs. The callback itself is never run while the lock is held. JS and
wasmJs remain single-threaded and use the same queue logic without requiring a
platform API in common code.

## Adversarial review

- Worker A then target B: A installs the claim before posting; B sees it and
  joins the queue, so A then B drain in that order.
- Target A reentrantly posts B: the claim stays held during A; B appends and
  runs after A returns, without recursive delivery.
- Concurrent producers: the lock gives each append one linearization order;
  one claim prevents concurrent callbacks and one active scheduled drain.
- Callback failure: `finally`-equivalent drain cleanup and continuation happen
  before the remembered failure is rethrown, so neither queued nor future work
  is stranded.
- Scheduler throw or Android rejection: the claim is cleared before the error
  escapes. The rejected callback remains queued and a later post retries it.
- Arrival at drain completion: under the lock, either the drainer sees pending
  work and keeps its claim for the continuation, or it releases an empty queue;
  a later arrival then safely claims a new drain.
- Unsubscribe and nested dispatch: the context preserves callback order only;
  `CallerSerializedStore`'s existing active registration check still suppresses
  queued callbacks after unsubscribe and nested dispatch joins the FIFO.
- Store closure/event-loop shutdown: no closure API exists; an adapter must
  reject by throwing rather than silently discard. Remaining work is retained
  for a later successful post.
- No counter is introduced, so overflow is impossible. A scheduler that calls
  its block synchronously is outside the documented asynchronous marshalling
  contract and cannot provide an event-loop yield.

## Implementation plan

1. Replace the helper's inline-or-post branch in
   `redux-kotlin-concurrent/.../NotificationContext.kt` with the common FIFO,
   claim, bounded-drain, and failure-recovery implementation; clarify KDocs.
2. Expand common concurrent tests with deterministic fake scheduling for mixed
   ordering, reentrancy, failures, bounded continuation, and completion races;
   add JVM contention coverage for one-at-a-time delivery.
3. Add store, granular, and Compose regression coverage using the corrected
   context; update TaskFlow Android rejection handling and all affected
   notification guidance/README wording.
4. Keep API dumps unchanged because the public syntax is unchanged; run target
   tests, lint, API verification, the full build, then review the final diff
   for claim/error-path races before publishing.
