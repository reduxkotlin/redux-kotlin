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
