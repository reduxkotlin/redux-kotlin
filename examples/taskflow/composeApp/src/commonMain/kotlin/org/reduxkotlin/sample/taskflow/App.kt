package org.reduxkotlin.sample.taskflow

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.reduxkotlin.Store
import org.reduxkotlin.compose.multimodel.fieldStateOf
import org.reduxkotlin.compose.rememberStableStore
import org.reduxkotlin.devtools.inapp.InAppConfig
import org.reduxkotlin.devtools.inapp.ReduxDevToolsHost
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.app.nav.AdaptiveNav
import org.reduxkotlin.sample.taskflow.app.nav.Back
import org.reduxkotlin.sample.taskflow.app.nav.Navigate
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AppSettingsModel
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.feature.account.AccountsModel
import org.reduxkotlin.sample.taskflow.feature.account.AuthMode
import org.reduxkotlin.sample.taskflow.feature.account.LoadAccountsSucceeded
import org.reduxkotlin.sample.taskflow.feature.account.LoginScreen
import org.reduxkotlin.sample.taskflow.feature.account.ProfileScreen
import org.reduxkotlin.sample.taskflow.feature.account.StartLogin
import org.reduxkotlin.sample.taskflow.feature.account.SwitcherScreen
import org.reduxkotlin.sample.taskflow.feature.board.BoardClosed
import org.reduxkotlin.sample.taskflow.feature.board.BoardScreen
import org.reduxkotlin.sample.taskflow.feature.board.CardDetailScreen
import org.reduxkotlin.sample.taskflow.feature.board.LoadBoardRequested
import org.reduxkotlin.sample.taskflow.feature.board.Refresh
import org.reduxkotlin.sample.taskflow.feature.boardlist.BoardListScreen
import org.reduxkotlin.sample.taskflow.feature.settings.SettingsScreen
import org.reduxkotlin.sample.taskflow.infra.SeedData
import org.reduxkotlin.sample.taskflow.infra.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.infra.data.local.SqlDelightLocalStore
import org.reduxkotlin.sample.taskflow.infra.db.taskFlowDb
import org.reduxkotlin.sample.taskflow.infra.platform.DriverFactory
import org.reduxkotlin.sample.taskflow.infra.util.DefaultIdGenerator
import org.reduxkotlin.sample.taskflow.store.AccountRegistry
import org.reduxkotlin.sample.taskflow.store.createAppStore
import org.reduxkotlin.sample.taskflow.store.getModel
import org.reduxkotlin.sample.taskflow.ui.Avatar
import org.reduxkotlin.sample.taskflow.ui.BackHandler
import org.reduxkotlin.sample.taskflow.ui.LocalClock
import org.reduxkotlin.sample.taskflow.ui.LocalIdGenerator
import org.reduxkotlin.sample.taskflow.ui.PredictiveBackHandler
import org.reduxkotlin.sample.taskflow.ui.adaptive.widthSizeClass
import org.reduxkotlin.sample.taskflow.ui.image.initCoil
import org.reduxkotlin.sample.taskflow.ui.theme.TaskFlowTheme
import kotlin.time.Clock

/**
 * Root composable for the TaskFlow sample: builds the root [createAppStore] store, asynchronously
 * stands up the durable [LocalStore], bootstraps the account directory, then binds the active
 * account's isolated store to the navigation shell, board lifecycle, periodic sync, and bot.
 *
 * Wiring discipline (Rule C/E): the singleton Coil loader is initialized first; every store read in
 * composition goes through [rememberStableStore] + a minimal [fieldStateOf] slice; subscriber
 * callbacks marshal to the main thread via each store's `NotificationContext`, so the background
 * effect/sync/bot coroutines can dispatch off-main safely.
 */
@Composable
public fun App() {
    // Mount the in-app DevTools drawer (edge-swipe + floating bubble). No instanceId = multi-session:
    // the drawer builds a registry over every hub session, so the always-present root store
    // ("TaskFlow-root") drives the trigger from launch and the store picker surfaces each account
    // store as it logs in. (Pinning instanceId to "TaskFlow" hid the bubble until an account existed.)
    ReduxDevToolsHost(InAppConfig()) {
        // Wire the singleton Coil ImageLoader before any AsyncImage composes.
        initCoil()

        val appStore = remember { createAppStore() }

        // Durable LocalStore is built off the suspending platform driver; splash until it is ready.
        val localStore by produceState<LocalStore?>(null) {
            val driver = DriverFactory().createDriver()
            value = SqlDelightLocalStore(taskFlowDb(driver))
        }

        val store = localStore
        if (store == null) {
            TaskFlowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        } else {
            AppShell(appStore = appStore, localStore = store)
        }
    }
}

/**
 * The post-bootstrap application shell, entered once the durable [localStore] exists.
 *
 * Owns the per-account [AccountRegistry], seeds the directory once, applies the root theme, and
 * routes on the active account's [NavModel]. Extracted from [App] so the splash branch stays small.
 *
 * @param appStore the root app store ([AccountsModel] / [AppSettingsModel] / auth flow).
 * @param localStore the durable offline cache shared across accounts.
 */
@Composable
private fun AppShell(appStore: Store<ModelState>, localStore: LocalStore) {
    val registry = remember(localStore) { AccountRegistry(appStore, localStore) }

    // Bootstrap once: ensure seed data, then rehydrate the account directory + active id (§13e).
    LaunchedEffect(localStore) {
        localStore.ensureSeeded()
        val activeId = localStore.loadActiveAccountId()
        val accounts = localStore.loadAccounts()
        appStore.dispatch(LoadAccountsSucceeded(accounts, activeId))
    }

    val theme by rememberStableStore(appStore).value.fieldStateOf(AppSettingsModel::class) { it.theme }
    val activeId by rememberStableStore(appStore).value.fieldStateOf(AccountsModel::class) { it.activeAccountId }

    TaskFlowTheme(theme = theme) {
        CompositionLocalProvider(
            LocalIdGenerator provides DefaultIdGenerator(),
            LocalClock provides { Clock.System.now() },
        ) {
            // Edge-to-edge: the root Surface fills the entire window (including behind the
            // status bar and navigation bar) so the bars show the theme colour through their
            // transparency rather than the activity's default window background. Inset handling
            // is owned by the leaf containers — [AdaptiveNav] tells its Scaffold to apply
            // `systemBars` to content, and its `NavigationBar` / `NavigationRail` pad themselves
            // via their default `windowInsets`. Screens must NOT reapply safeDrawing at their
            // root or insets would be applied twice.
            Surface(modifier = Modifier.fillMaxSize()) {
                val id = activeId
                if (id == null) {
                    LoginScreen(appStore)
                } else {
                    // Persist the active account id for process-death rehydration (§13e).
                    LaunchedEffect(id) { localStore.saveActiveAccountId(id) }
                    ActiveAccount(appStore = appStore, registry = registry, activeId = id)
                }
            }
        }

        // Logout: when an account drops out of AccountsModel, tear its store + coroutines down.
        AccountDisposalEffect(appStore = appStore, registry = registry)
    }
}

/**
 * Renders the active account [activeId]: builds (or reuses) its isolated store, drives the
 * navigation shell, board lifecycle (load + bot start/stop), periodic sync, and the card-detail
 * and switcher overlays.
 *
 * @param appStore the root app store (read for the switcher + add-account flow).
 * @param registry the per-account store registry.
 * @param activeId the currently active account.
 */
@Composable
private fun ActiveAccount(appStore: Store<ModelState>, registry: AccountRegistry, activeId: AccountId) {
    val handle = remember(activeId) { registry.getOrCreate(activeId, SeedData.accountDetail(activeId)) }
    val accountStore = handle.store

    val nav by rememberStableStore(accountStore).value.fieldStateOf(NavModel::class) { it }

    // Lifecycle effects key on the *active board* (the lowest Board in the stack), not the top of
    // the stack — so drilling into CardDetail/ComposeCard keeps the board loaded and the sync ticking.
    BoardLifecycleEffect(
        accountStore = accountStore,
        registry = registry,
        activeId = activeId,
        activeBoardId = nav.activeBoardId,
    )
    PeriodicSyncEffect(appStore = appStore, accountStore = accountStore, activeBoardId = nav.activeBoardId)

    var showSwitcher by remember { mutableStateOf(false) }

    // System back pops a stack frame (or flips CardDetail Edit -> View — see [navReducer]). Disabled
    // at the root so the host's system back exits / backgrounds the app.
    BackHandler(enabled = nav.stack.size > 1) { accountStore.dispatch(Back) }

    BoxWithConstraintsRouting(
        appStore = appStore,
        accountStore = accountStore,
        nav = nav,
        onOpenSwitcher = { showSwitcher = true },
    )

    if (showSwitcher) {
        Dialog(onDismissRequest = { showSwitcher = false }) {
            SwitcherScreen(
                rootStore = appStore,
                statusLineFor = { id ->
                    registry.get(id)
                        ?.let { runCatching { it.getModel<NavModel>().current }.getOrNull() }
                        ?.let { routeLabel(it) }
                        ?: ""
                },
                onAddAccount = {
                    showSwitcher = false
                    appStore.dispatch(StartLogin(AuthMode.AddAccount))
                },
            )
        }
    }
}

/**
 * The adaptive nav shell hosting the routed screen plus any modal overlay.
 *
 * The *background* is the bottom-most [Route.TopLevel] in the stack — that's the screen the
 * AdaptiveNav highlights and the bg of any overlay. The *overlay* is whatever's stacked on top
 * (a [Route.CardDetail] or [Route.ComposeCard]); `null` when the user is on a top-level screen.
 *
 * @param appStore the root app store (Profile/Settings read root models).
 * @param accountStore the active account store.
 * @param nav the per-account [NavModel] (drives both background and overlay).
 * @param onOpenSwitcher opens the account-switcher overlay.
 */
@Composable
private fun BoxWithConstraintsRouting(
    appStore: Store<ModelState>,
    accountStore: Store<ModelState>,
    nav: NavModel,
    onOpenSwitcher: () -> Unit,
) {
    // The nav-rail / nav-bar highlight tracks the deepest TopLevel — both BoardList and Board
    // match the Boards tab via [AdaptiveNav.matches], so this picks the right tab in either case.
    val tabBackground: Route.TopLevel = nav.stack.last { it is Route.TopLevel } as Route.TopLevel
    val layers: PersistentList<Route> = nav.stack

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wsc = widthSizeClass(maxWidth)
        AdaptiveNav(
            sizeClass = wsc,
            currentRoute = tabBackground,
            onNavigate = { accountStore.dispatch(Navigate(it)) },
            header = { SwitcherAvatarButton(appStore = appStore, onOpenSwitcher = onOpenSwitcher) },
        ) {
            // Render every layer in the stack from bottom to top. Only the top layer is animated
            // by the [PredictiveBackHandler]; lower layers stay put and are revealed as the top
            // layer slides off. `key(route)` isolates each layer's Compose state so going Board
            // -> CardDetail -> back to Board preserves Board's scroll position, etc., and the
            // CardDetail View<->Edit mode flip resets local UI state via its own key change.
            Box(modifier = Modifier.fillMaxSize()) {
                layers.forEachIndexed { index, route ->
                    val isTop = index == layers.lastIndex
                    val canPop = isTop && layers.size > 1
                    key(route) {
                        if (canPop) {
                            // Intra-destination: when the top is a CardDetail in Edit mode, the
                            // gesture's destination is the **same card in View mode**, not the
                            // board beneath the stack. Render an explicit `CardDetail(View)` here
                            // as a static backdrop so the ModeFlip animation reveals it instead
                            // of the board.
                            val editingCard = (route as? Route.CardDetail)
                                ?.takeIf { it.mode == Route.CardDetail.Mode.Edit }
                            if (editingCard != null) {
                                CardDetailScreen(
                                    store = accountStore,
                                    route = Route.CardDetail(editingCard.cardId, Route.CardDetail.Mode.View),
                                )
                            }
                            OverlayWithPredictiveBack(
                                onBack = { accountStore.dispatch(Back) },
                                kind = if (editingCard != null) PredictiveBackKind.ModeFlip else PredictiveBackKind.Pop,
                            ) {
                                RouteScreen(route, appStore, accountStore)
                            }
                        } else {
                            RouteScreen(route, appStore, accountStore)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Routes a single [Route] to its host screen. Extracted so both the static (lower) layers and the
 * animated (top) layer dispatch through one renderer.
 *
 * Every [Route.TopLevel] is wrapped in a fullscreen [Surface] so its background is opaque,
 * regardless of where it sits in the stack — otherwise any transparent area of the screen
 * (gaps between cards, the empty bottom of a column, etc.) would let the layer beneath show
 * through. [Route.CardDetail] / [Route.ComposeCard] are intentionally **not** wrapped: their
 * own [Surface] paints the side sheet on Medium / Expanded layouts and the board stays visible
 * to the side of the sheet by design.
 */
@Composable
private fun RouteScreen(route: Route, appStore: Store<ModelState>, accountStore: Store<ModelState>) {
    val content: @Composable () -> Unit = {
        when (route) {
            is Route.BoardList -> BoardListScreen(accountStore)
            is Route.Board -> BoardScreen(accountStore)
            Route.Profile -> ProfileScreen(accountStore, appStore)
            Route.Settings -> SettingsScreen(appStore)
            is Route.CardDetail, is Route.ComposeCard -> CardDetailScreen(accountStore)
        }
    }
    if (route is Route.TopLevel) {
        Surface(modifier = Modifier.fillMaxSize()) { content() }
    } else {
        content()
    }
}

/**
 * Wraps the top layer of the nav stack with Android predictive-back animation. The visual depends
 * on [kind]:
 *  - [PredictiveBackKind.Pop] — horizontal slide + scale + fade. Cross-destination motion: the
 *    user sees the previous destination revealed beneath.
 *  - [PredictiveBackKind.ModeFlip] — in-place fade + small scale, no translation. Intra-destination
 *    motion: the layer dissolves to reveal the same screen in a different state rendered beneath.
 *
 * On non-Android targets the [PredictiveBackHandler] is a no-op, so the layer renders at its
 * resting transform (no animation, no interception). The app-level [BackHandler] is still wired
 * elsewhere for terminal back on those platforms.
 *
 * @param onBack invoked once the gesture commits past the threshold.
 * @param kind motion variant — defaults to [PredictiveBackKind.Pop].
 * @param content the layer body to render and animate.
 */
@Composable
private fun OverlayWithPredictiveBack(
    onBack: () -> Unit,
    kind: PredictiveBackKind = PredictiveBackKind.Pop,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    PredictiveBackHandler(
        enabled = true,
        onProgress = { p -> scope.launch { progress.snapTo(p) } },
        onBack = onBack,
        onCancel = { scope.launch { progress.animateTo(0f) } },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value
                when (kind) {
                    PredictiveBackKind.Pop -> {
                        translationX = p * size.width
                        scaleX = 1f - OVERLAY_BACK_SCALE * p
                        scaleY = 1f - OVERLAY_BACK_SCALE * p
                        alpha = 1f - OVERLAY_BACK_ALPHA * p
                    }

                    PredictiveBackKind.ModeFlip -> {
                        // No translation: this is a state change on the same screen, not a pop.
                        // Subtle scale + a full fade so the destination state beneath becomes visible.
                        scaleX = 1f - MODE_FLIP_SCALE * p
                        scaleY = 1f - MODE_FLIP_SCALE * p
                        alpha = 1f - p
                    }
                }
            },
    ) {
        content()
    }
}

private enum class PredictiveBackKind { Pop, ModeFlip }

private const val OVERLAY_BACK_SCALE = 0.05f
private const val OVERLAY_BACK_ALPHA = 0.4f
private const val MODE_FLIP_SCALE = 0.03f

/**
 * The active-account avatar that opens the account switcher. Rendered as the [AdaptiveNav] rail
 * header on wide layouts; a tap opens the switcher overlay.
 *
 * @param appStore the root app store, read for the active account summary.
 * @param onOpenSwitcher opens the switcher overlay.
 */
@Composable
private fun SwitcherAvatarButton(appStore: Store<ModelState>, onOpenSwitcher: () -> Unit) {
    val accounts by rememberStableStore(appStore).value.fieldStateOf(AccountsModel::class) { it }
    val active = accounts.activeAccountId?.let { accounts.accounts[it] }
    IconButton(onClick = onOpenSwitcher) {
        if (active != null) {
            Avatar(
                name = active.displayName,
                avatarUrl = active.avatarUrl,
                seedId = active.id.v,
            )
        } else {
            Text("☰")
        }
    }
}

/**
 * Drives the board lifecycle for the active account: when [activeBoardId] becomes non-null
 * (a [Route.Board] is anywhere in the nav stack), loads the board and starts the bot; when it
 * goes back to null (or changes id), the previous `DisposableEffect.onDispose` fires
 * [BoardClosed] and stops the bot.
 *
 * Keying on the **board id** rather than the full route means descending into a card detail or
 * compose-card overlay keeps the board loaded — the board only unloads when the user truly
 * leaves the boards stack (back past the Board frame, or switches to Profile/Settings).
 *
 * @param accountStore the active account store.
 * @param registry the per-account registry (bot start/stop).
 * @param activeId the active account.
 * @param activeBoardId id of the active board, or `null` if no board is on the stack.
 */
@Composable
private fun BoardLifecycleEffect(
    accountStore: Store<ModelState>,
    registry: AccountRegistry,
    activeId: AccountId,
    activeBoardId: BoardId?,
) {
    DisposableEffect(activeId, activeBoardId) {
        if (activeBoardId != null) {
            accountStore.dispatch(LoadBoardRequested(activeBoardId))
            registry.startBot(activeId, rngSeed = 0L)
        }
        onDispose {
            if (activeBoardId != null) {
                accountStore.dispatch(BoardClosed)
                registry.stopBot(activeId)
            }
        }
    }
}

/**
 * Periodic sync: while a board is on the nav stack (i.e. [activeBoardId] is non-null), ticks
 * [Refresh] every `syncIntervalMs` (Rule F: >= 10 s, cancelled when the user leaves the boards
 * stack). Flipping the fake service online also kicks an immediate [Refresh] to drain the queue.
 *
 * Keying on the board id (not the top-of-stack route) keeps the sync ticking while the user has
 * a card detail / compose-card overlay open — those don't leave the board.
 *
 * @param appStore the root app store, read for the live `syncIntervalMs` / `online` knobs.
 * @param accountStore the active account store (the [Refresh] target).
 * @param activeBoardId id of the active board, or `null` if no board is on the stack.
 */
@Composable
private fun PeriodicSyncEffect(appStore: Store<ModelState>, accountStore: Store<ModelState>, activeBoardId: BoardId?) {
    val online by rememberStableStore(appStore).value.fieldStateOf(AppSettingsModel::class) { it.fakeService.online }
    val intervalMs by rememberStableStore(appStore).value
        .fieldStateOf(AppSettingsModel::class) { it.fakeService.syncIntervalMs }

    LaunchedEffect(activeBoardId, intervalMs) {
        if (activeBoardId != null) {
            val tick = intervalMs.coerceAtLeast(MIN_SYNC_INTERVAL_MS).toLong()
            while (isActive) {
                delay(tick)
                accountStore.dispatch(Refresh)
            }
        }
    }

    // Coming back online kicks an immediate sync so the queue drains promptly.
    LaunchedEffect(online, activeBoardId) {
        if (online && activeBoardId != null) accountStore.dispatch(Refresh)
    }
}

/**
 * Logout disposal: remembers the previous account-id set and, when an account disappears from
 * [AccountsModel], removes its store from the [registry] (cancelling its scope + bot).
 *
 * @param appStore the root app store holding [AccountsModel].
 * @param registry the registry whose dropped accounts must be torn down.
 */
@Composable
private fun AccountDisposalEffect(appStore: Store<ModelState>, registry: AccountRegistry) {
    val ids by rememberStableStore(appStore).value.fieldStateOf(AccountsModel::class) { it.accounts.keys }
    var previous by remember { mutableStateOf<Set<AccountId>>(emptySet()) }
    LaunchedEffect(ids) {
        val removed = previous - ids
        removed.forEach { registry.remove(it) }
        previous = ids
    }
}

/** Minimum periodic-sync tick (Rule F floors the configurable interval at 10 s). */
private const val MIN_SYNC_INTERVAL_MS: Int = 10_000

/**
 * A short human label for a [route], used as the switcher status line ("on Board", "on Settings").
 *
 * @param route the per-account route to label.
 * @return a short display label for [route].
 */
internal fun routeLabel(route: Route): String = when (route) {
    is Route.BoardList -> "on Boards"
    is Route.Board -> "on Board"
    Route.Profile -> "on Profile"
    Route.Settings -> "on Settings"
    is Route.CardDetail -> if (route.mode == Route.CardDetail.Mode.Edit) "editing a card" else "viewing a card"
    is Route.ComposeCard -> "composing a card"
}
