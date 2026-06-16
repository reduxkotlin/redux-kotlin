package org.reduxkotlin.sample.taskflow.snapshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import coil3.compose.setSingletonImageLoaderFactory
import kotlinx.collections.immutable.toPersistentMap
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
import org.reduxkotlin.snapshot.SnapshotApp
import org.reduxkotlin.snapshot.SnapshotInput
import org.reduxkotlin.snapshot.snapshotApp

private const val FAILING_RATE = 0.9f
private const val OFFLINE_MAX_LATENCY_MS = 3000

/**
 * Snapshot scenes for the TaskFlow sample — the reference integration of `redux-kotlin-snapshot`.
 * Each scene seeds a *real* TaskFlow store and renders the real screen; the frame is a pure function
 * of the dispatched state (the harder, store-bound case the library exists for).
 */
public val taskFlowSnapshots: SnapshotApp = snapshotApp {
    defaults {
        width = 411
        height = 891
        density = 2f
        theme = "dark"
    }
    scene("board") {
        presets("seeded", "empty")
        render { args -> boardScene(preset(args, "seeded"), theme(args.theme)) }
    }
    scene("settings") {
        presets("default", "offline-failing", "online-bot")
        render { args -> settingsScene(preset(args, "default"), theme(args.theme)) }
    }
}

private fun preset(args: org.reduxkotlin.snapshot.SceneArgs, fallback: String): String =
    (args.input as? SnapshotInput.Preset)?.name ?: fallback

private fun theme(name: String?): Theme = when (name) {
    "light" -> Theme.Light
    "dark" -> Theme.Dark
    else -> Theme.System
}

private fun boardScene(preset: String, theme: Theme): @Composable () -> Unit {
    val seeded = SeedData.seededAccounts().first()
    val owner = seeded.owner.id
    // Every model BoardScreen reads must be registered, or ModelState.get throws in composition.
    val store: Store<ModelState> = createConcurrentModelStore(notificationContext = NotificationContext.Inline) {
        model(BoardModel()) { on<LoadBoardSucceeded> { s, a -> boardReducer(s, a, owner) } }
        model(CollaboratorsModel(byId = seeded.collaborators.associateBy { it.id }.toPersistentMap())) {}
        model(BoardListModel()) {}
        model(FilterModel()) {}
        model(SyncModel()) {}
        model(ActivityModel()) {}
        model(UndoModel()) {}
    }
    if (preset != "empty") store.dispatch(LoadBoardSucceeded(seeded.board))
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

private fun settingsScene(preset: String, theme: Theme): @Composable () -> Unit {
    val store = createAppStore(NotificationContext.Inline)
    store.dispatch(SetTheme(theme))
    when (preset) {
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

        else -> Unit
    }
    return { Themed(theme) { SettingsScreen(store) } }
}

@Composable
private fun Themed(theme: Theme, body: @Composable () -> Unit) {
    TaskFlowTheme(theme = theme, dynamic = false) {
        Surface(modifier = Modifier.fillMaxSize()) { body() }
    }
}
