# Granular selector work design

## Problem and baseline

`subscribeTo` is implemented as a one-entry `subscribeFields` block. Therefore
N independent subscriptions install N store subscribers. A dispatch invokes N
store callbacks and evaluates each selector, even when only one selected value
changes. `selectorState` and `fieldState` each use that independent path and
also pull their selector again when Compose reads `State.value`.

The existing `subscribeFields` API proves that one store callback can fan out
to many selectors safely, but its entries are fixed at registration time and
cannot serve a changing Compose subtree.

The implementation pins this baseline with deterministic counters:

| Shape | Store callbacks per dispatch | Selector evaluations | Expensive transforms |
| --- | ---: | ---: | ---: |
| N independent `subscribeTo` calls | N | N | N for ordinary selectors |
| One `SelectorSubscriptions` scope | 1 | N | N for ordinary selectors |
| Memoized selector with unchanged declared input | 1 or N | input checks still run | 0 after the first result |

The second row is deliberately not described as selector-evaluation
elimination. Without explicit dependencies, evaluating an arbitrary selector
is required for correctness. The third row is the opt-in reduction for a
meaningful expensive transform.

## Chosen API

`redux-kotlin-granular` gains:

* `Store<S>.selectorSubscriptions()` returning a lifecycle-owned
  `SelectorSubscriptions<S>` registry. Dynamic entries share one underlying
  store subscriber, unsubscribe when empty, and can be closed with the owning
  UI subtree.
* `memoizedSelector(input, transform)` and a two-input overload. They return a
  stable function object accepted by all existing selector APIs. A transform is
  re-run only when an explicitly declared input changed under the same
  referential-then-structural equality policy used by granular subscriptions.

`redux-kotlin-compose` gains:

* `rememberSelectorSubscriptions()`, which ties the registry to Compose
  disposal.
* `selectorState(subscriptions, selector)` and
  `fieldState(subscriptions, property)` overloads. A screen hoists one scope
  and passes it to sibling bindings that should share one store callback.
* keyed `selectorState(key, selector)` overloads so a selector that closes over
  a changing parameter is recreated and resubscribed instead of silently
  retaining its first closure.

The multi-model companion modules expose matching scoped convenience
overloads. Existing APIs keep their behavior and binary signatures.

## Correctness and concurrency

`SelectorSubscriptions` uses an AtomicFu lock for registry mutation. It takes
an entry snapshot before evaluation and never invokes a consumer listener
while holding that lock, so a listener can dispatch or dispose itself. An entry
is re-sampled after its underlying store subscription is installed, preserving
the existing registration-window guarantee. Closing or removing an entry makes
already captured entries inert before they can notify.

The scope depends on the existing store contract: notification delivery is
serial and post-ordered. It subscribes to the final `Store<S>` supplied by the
caller, so it works whether `createConcurrentStore`, routing, or a bundle
wrapped the store first. No enhancer wrapper or global store registry is used.

Memoized selectors use an immutable atomic cache. Concurrent readers may race
and compute the same transform more than once, but they cannot return a result
for a different declared input. Avoiding duplicate transform work across
threads would require serializing an application-provided transform and is not
part of this API.

## Rejected alternatives

* Automatic selector dependency tracking cannot safely infer reads from an
  arbitrary Kotlin lambda; it would either miss dependencies or require a new
  state representation.
* Replacing the marker enhancer with a store wrapper risks being bypassed by
  later concurrent/routing wrappers and makes subscription topology depend on
  enhancer order. The explicit final-store scope has no such ambiguity.
* `ModelState` slot versions would change a shared state representation and
  still would not prove arbitrary selector dependencies. The generic explicit
  input path already supports model selectors without that cost.
* Flow adapters add another lifecycle and scheduling model. They are separate
  from reducing store callback and transform work, so they remain out of
  scope.

## Verification

Tests cover the callback-count baseline, selector and transform counters,
scope lifecycle, registration races, unsubscribe behavior, keyed Compose
selectors, and concurrent-store notification ordering. JMH benchmarks compare
independent versus shared fan-out and plain versus memoized transforms under an
unchanged-input dispatch workload. Public API dumps, targeted module tests,
`detektAll`, `apiCheck`, and the full build remain required gates.
