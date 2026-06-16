@file:OptIn(ExperimentalComposeUiApi::class)

package org.reduxkotlin.sample.taskflow.render

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import coil3.compose.setSingletonImageLoaderFactory
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.skia.EncodedImageFormat
import org.reduxkotlin.Store
import org.reduxkotlin.bundle.createConcurrentModelStore
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.app.createAppStore
import org.reduxkotlin.sample.taskflow.core.Theme
import org.reduxkotlin.sample.taskflow.feature.activity.ActivityModel
import org.reduxkotlin.sample.taskflow.feature.board.BoardModel
import org.reduxkotlin.sample.taskflow.feature.board.BoardScreen
import org.reduxkotlin.sample.taskflow.feature.board.FilterModel
import org.reduxkotlin.sample.taskflow.feature.board.LoadBoardSucceeded
import org.reduxkotlin.sample.taskflow.feature.board.SyncModel
import org.reduxkotlin.sample.taskflow.feature.board.boardReducer
import org.reduxkotlin.sample.taskflow.feature.boardlist.BoardListModel
import org.reduxkotlin.sample.taskflow.feature.collaborators.CollaboratorsModel
import org.reduxkotlin.sample.taskflow.feature.settings.SetBotEnabled
import org.reduxkotlin.sample.taskflow.feature.settings.SetFailureRate
import org.reduxkotlin.sample.taskflow.feature.settings.SetLatency
import org.reduxkotlin.sample.taskflow.feature.settings.SetOnline
import org.reduxkotlin.sample.taskflow.feature.settings.SetTheme
import org.reduxkotlin.sample.taskflow.feature.settings.SettingsScreen
import org.reduxkotlin.sample.taskflow.feature.undo.UndoModel
import org.reduxkotlin.sample.taskflow.infra.SeedData
import org.reduxkotlin.sample.taskflow.infra.util.FakeIdGenerator
import org.reduxkotlin.sample.taskflow.ui.LocalClock
import org.reduxkotlin.sample.taskflow.ui.LocalIdGenerator
import org.reduxkotlin.sample.taskflow.ui.image.fakeNoNetworkImageLoader
import org.reduxkotlin.sample.taskflow.ui.theme.TaskFlowTheme

/** Renders [content] off-screen at [width]x[height] device pixels (2x density) and PNG-encodes it. */
internal fun renderToPng(width: Int, height: Int, content: @Composable () -> Unit): ByteArray {
    val scene = ImageComposeScene(width = width, height = height, density = Density(RENDER_DENSITY)) {
        content()
    }
    return try {
        scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
    } finally {
        scene.close()
    }
}

/**
 * Settings screen scene. With [json] present the state is fully agent-driven via a [SettingsSpec]
 * (each non-null field dispatched; `theme` in the spec overrides the [theme] flag); otherwise the
 * named [state] preset is applied.
 */
internal fun settingsScene(state: String, theme: Theme, json: JsonElement?): @Composable () -> Unit {
    val store = createAppStore(NotificationContext.Inline)
    var resolvedTheme = theme
    if (json != null) {
        val spec = LENIENT_JSON.decodeFromJsonElement<SettingsSpec>(json)
        resolvedTheme = spec.theme?.let { parseTheme(it) } ?: theme
        store.dispatch(SetTheme(resolvedTheme))
        spec.online?.let { store.dispatch(SetOnline(it)) }
        spec.botEnabled?.let { store.dispatch(SetBotEnabled(it)) }
        spec.failureRate?.let { store.dispatch(SetFailureRate(it)) }
        if (spec.latencyMinMs != null || spec.latencyMaxMs != null) {
            store.dispatch(SetLatency(spec.latencyMinMs ?: 0, spec.latencyMaxMs ?: DEFAULT_LATENCY_MAX_MS))
        }
    } else {
        store.dispatch(SetTheme(theme))
        applySettingsPreset(store, state)
    }
    val finalTheme = resolvedTheme
    return { Themed(finalTheme) { SettingsScreen(store) } }
}

/** Applies a named settings preset by dispatching the corresponding actions. */
private fun applySettingsPreset(store: Store<ModelState>, preset: String) {
    when (preset) {
        "default" -> Unit

        "offline-failing" -> {
            store.dispatch(SetOnline(false))
            store.dispatch(SetBotEnabled(false))
            store.dispatch(SetFailureRate(FAILING_RATE))
            store.dispatch(SetLatency(0, OFFLINE_MAX_LATENCY_MS))
        }

        "online-bot" -> {
            store.dispatch(SetOnline(true))
            store.dispatch(SetBotEnabled(true))
            store.dispatch(SetFailureRate(0f))
        }

        else -> fail("unknown settings --state '$preset' (default|offline-failing|online-bot)")
    }
}

/**
 * Board screen scene: a minimal account-shaped concurrent store wired to the real [boardReducer],
 * seeded from [SeedData] (the `empty` preset leaves [BoardModel] unloaded to render the empty
 * state). Demonstrates the harder case — a rich screen with cards, labels, avatars — still renders
 * purely from seeded state, with the singleton image loader swapped for a no-network one.
 */
internal fun boardScene(state: String, theme: Theme, json: JsonElement?): @Composable () -> Unit {
    val seeded = SeedData.seededAccounts().first()
    val owner = seeded.owner.id
    // BoardScreen reads all of these slices via fieldStateOf/selectorState; every model it reads
    // MUST be registered or ModelState.get throws inside composition and the recomposer spins.
    val store: Store<ModelState> = createConcurrentModelStore(notificationContext = NotificationContext.Inline) {
        model(BoardModel()) {
            on<LoadBoardSucceeded> { s, a -> boardReducer(s, a, owner) }
        }
        model(CollaboratorsModel(byId = seeded.collaborators.associateBy { it.id }.toPersistentMap())) {}
        model(BoardListModel()) {}
        model(FilterModel()) {}
        model(SyncModel()) {}
        model(ActivityModel()) {}
        model(UndoModel()) {}
    }
    // With json present the agent fully authors the board; otherwise the named preset is used.
    val board = when {
        json != null -> buildBoardFromSpec(LENIENT_JSON.decodeFromJsonElement<BoardSpec>(json))
        state == "seeded" -> seeded.board
        state == "empty" -> null
        else -> fail("unknown board --state '$state' (seeded|empty)")
    }
    if (board != null) store.dispatch(LoadBoardSucceeded(board))
    return {
        CompositionLocalProvider(
            LocalIdGenerator provides FakeIdGenerator(),
            LocalClock provides { SeedData.SEED_INSTANT },
        ) {
            setSingletonImageLoaderFactory { ctx -> fakeNoNetworkImageLoader(ctx) }
            Themed(theme) { BoardScreen(store) }
        }
    }
}

/** Wraps [body] in the app theme + a full-bleed surface so the captured frame has an opaque bg. */
@Composable
private fun Themed(theme: Theme, body: @Composable () -> Unit) {
    TaskFlowTheme(theme = theme, dynamic = false) {
        Surface(modifier = Modifier.fillMaxSize()) { body() }
    }
}

private val LENIENT_JSON = Json { ignoreUnknownKeys = true }

private const val RENDER_DENSITY = 2f
private const val FAILING_RATE = 0.9f
private const val OFFLINE_MAX_LATENCY_MS = 3000
private const val DEFAULT_LATENCY_MAX_MS = 800
