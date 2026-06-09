package org.reduxkotlin.compose.saveable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import org.reduxkotlin.Store

// Alphanumeric encoding; Int/Long.toString(radix) is defined on all KMP targets.
private const val KEY_RADIX = 36

/**
 * Anchors saveable persistence for [this] store to the enclosing
 * [SaveableStateRegistry]. Place it **once** per persisted scope (typically
 * near the root, or once per screen).
 *
 * On a real restore (rotation or process death) the saved snapshot is decoded
 * and dispatched via [StateSaver.restore] exactly once, so downstream bindings
 * observe the rehydrated store (a single stale frame is possible before they
 * re-sample). On cold start nothing is dispatched. The latest projection is
 * provided to the registry and serialized only when the platform actually
 * saves — there is no steady-state subscription.
 *
 * The persisted store must accept main-thread reads and dispatch (the
 * Compose-facing store: concurrent/threadsafe, or main-confined).
 *
 * Example:
 * ```
 * @Composable
 * fun App(store: Store<AppState>) {
 *     store.rememberSaveableState(uiSaver)
 *     // … screen content; child fieldState bindings see the rehydrated store
 * }
 * ```
 *
 * [saver] describes the snapshot projection and restore action.
 * [key] is a stable registry key required when multiple anchors exist, inside
 * lists, or across navigation where positional keys collide. Defaults to the
 * call-site composite key.
 */
@Composable
public fun <S, Snapshot : Any> Store<S>.rememberSaveableState(saver: StateSaver<S, Snapshot>, key: String? = null) {
    val store = this
    val registry = LocalSaveableStateRegistry.current
    val finalKey = key ?: currentCompositeKeyHash.toString(KEY_RADIX)
    DisposableEffect(store, registry, finalKey) {
        val entry = wireSaveable(store, registry, finalKey, saver)
        onDispose { entry?.unregister() }
    }
}

/**
 * Non-composable core: consume any restored snapshot and dispatch the restore
 * action exactly once (only on a real restore), then register the save
 * provider. Extracted so the correctness path is testable without a
 * composition. Returns the provider entry to unregister on dispose.
 */
internal fun <S, Snapshot : Any> wireSaveable(
    store: Store<S>,
    registry: SaveableStateRegistry?,
    key: String,
    saver: StateSaver<S, Snapshot>,
): SaveableStateRegistry.Entry? {
    val restored = (registry?.consumeRestored(key) as? String)?.let { encoded ->
        runCatching { saver.json.decodeFromString(saver.serializer, encoded) }.getOrNull()
    }
    if (restored != null) {
        store.dispatch(saver.restore(restored))
    }
    return registry?.registerProvider(key) {
        runCatching { saver.json.encodeToString(saver.serializer, saver.save(store.state)) }.getOrNull()
    }
}
