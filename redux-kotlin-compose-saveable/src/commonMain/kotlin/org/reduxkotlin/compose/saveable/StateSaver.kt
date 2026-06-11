package org.reduxkotlin.compose.saveable

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Describes how a slice of store state [S] is projected to a small
 * serializable [Snapshot], and how a restored snapshot is turned back into
 * an action the store's reducer applies.
 *
 * Holds no Compose state — its serialization round-trip is unit-testable
 * without a composition. Reuse one instance across screens.
 *
 * Restore-action contract:
 * - The action returned by [restore] is dispatched like any other — it flows
 *   through the **full middleware chain**, so an effects middleware may match
 *   it to re-trigger loads (the alternative is keying load effects on the
 *   restored state itself; see `rememberSaveableState`'s docs).
 * - A restore replays **only** this one action; the events that originally
 *   produced the saved state are not replayed.
 * - A snapshot can outlive the data it references (a deleted item, a removed
 *   screen target). Whatever handles the restore action — and anything loaded
 *   from it — must tolerate stale references: treat "not found" as an empty
 *   state or a navigation back, never a crash.
 *
 * Example:
 * ```
 * @Serializable
 * data class UiSnapshot(val tab: Int, val query: String)
 *
 * val uiSaver = StateSaver(
 *     serializer = UiSnapshot.serializer(),
 *     save = { s: AppState -> UiSnapshot(s.tab, s.query) },
 *     restore = { RehydrateUi(it.tab, it.query) }, // your reducer applies this action
 * )
 * ```
 */
public class StateSaver<S, Snapshot : Any>(
    /** Serializer for the [Snapshot] type (e.g. `MySnapshot.serializer()`). */
    public val serializer: KSerializer<Snapshot>,
    /** Projects current state to the minimal snapshot worth persisting. */
    public val save: (S) -> Snapshot,
    /** Turns a restored snapshot into an action the reducer applies. */
    public val restore: (Snapshot) -> Any,
    /** JSON codec; override to tune (e.g. `Json { ignoreUnknownKeys = true }`). */
    public val json: Json = Json,
)
