package org.reduxkotlin.sample.taskflow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.reduxkotlin.Store
import org.reduxkotlin.compose.multimodel.fieldStateOf
import org.reduxkotlin.compose.rememberStableStore
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.BoardClosed
import org.reduxkotlin.sample.taskflow.action.LoadAccountsSucceeded
import org.reduxkotlin.sample.taskflow.action.LoadBoardRequested
import org.reduxkotlin.sample.taskflow.action.Navigate
import org.reduxkotlin.sample.taskflow.action.Refresh
import org.reduxkotlin.sample.taskflow.action.StartLogin
import org.reduxkotlin.sample.taskflow.data.SeedData
import org.reduxkotlin.sample.taskflow.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.data.local.SqlDelightLocalStore
import org.reduxkotlin.sample.taskflow.db.taskFlowDb
import org.reduxkotlin.sample.taskflow.model.AccountId
import org.reduxkotlin.sample.taskflow.model.AccountsModel
import org.reduxkotlin.sample.taskflow.model.AppSettingsModel
import org.reduxkotlin.sample.taskflow.model.AuthMode
import org.reduxkotlin.sample.taskflow.model.NavModel
import org.reduxkotlin.sample.taskflow.model.Route
import org.reduxkotlin.sample.taskflow.platform.DriverFactory
import org.reduxkotlin.sample.taskflow.store.AccountRegistry
import org.reduxkotlin.sample.taskflow.store.createAppStore
import org.reduxkotlin.sample.taskflow.store.getModel
import org.reduxkotlin.sample.taskflow.ui.LocalClock
import org.reduxkotlin.sample.taskflow.ui.LocalIdGenerator
import org.reduxkotlin.sample.taskflow.ui.adaptive.widthSizeClass
import org.reduxkotlin.sample.taskflow.ui.components.AdaptiveNav
import org.reduxkotlin.sample.taskflow.ui.components.Avatar
import org.reduxkotlin.sample.taskflow.ui.image.initCoil
import org.reduxkotlin.sample.taskflow.ui.screens.BoardListScreen
import org.reduxkotlin.sample.taskflow.ui.screens.BoardScreen
import org.reduxkotlin.sample.taskflow.ui.screens.CardDetailScreen
import org.reduxkotlin.sample.taskflow.ui.screens.LoginScreen
import org.reduxkotlin.sample.taskflow.ui.screens.ProfileScreen
import org.reduxkotlin.sample.taskflow.ui.screens.SettingsScreen
import org.reduxkotlin.sample.taskflow.ui.screens.SwitcherScreen
import org.reduxkotlin.sample.taskflow.ui.theme.TaskFlowTheme
import org.reduxkotlin.sample.taskflow.util.DefaultIdGenerator
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
    // Wire the singleton Coil ImageLoader before any AsyncImage composes.
    initCoil()

    val appStore = remember { createAppStore() }

    // Durable LocalStore is built off the suspending platform driver; show a splash until it is ready.
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
        return
    }

    AppShell(appStore = appStore, localStore = store)
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
            // Single safe-area application point (Rule H): the shell root pads for the
            // system insets so the nav rail/bar and routed content both respect the safe
            // area. Screens (and the AdaptiveNav Scaffold) must NOT reapply safeDrawing,
            // which would double the insets.
            Surface(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
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

    val route by rememberStableStore(accountStore).value.fieldStateOf(NavModel::class) { it.route }
    val nav by rememberStableStore(accountStore).value.fieldStateOf(NavModel::class) { it }

    // Board lifecycle: entering a board loads it + starts the bot; leaving fires BoardClosed + stops it.
    BoardLifecycleEffect(accountStore = accountStore, registry = registry, activeId = activeId, route = route)

    // Periodic sync tick while a board is open (Rule F: >= 10s, cancelled on leave).
    PeriodicSyncEffect(appStore = appStore, accountStore = accountStore, route = route)

    var showSwitcher by remember { mutableStateOf(false) }

    BoxWithConstraintsRouting(
        appStore = appStore,
        accountStore = accountStore,
        route = route,
        showCardDetail = nav.openCardId != null || nav.composing != null,
        onOpenSwitcher = { showSwitcher = true },
    )

    if (showSwitcher) {
        Dialog(onDismissRequest = { showSwitcher = false }) {
            SwitcherScreen(
                rootStore = appStore,
                statusLineFor = { id ->
                    registry.get(id)
                        ?.let { runCatching { it.getModel<NavModel>().route }.getOrNull() }
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
 * The adaptive nav shell hosting the routed screen plus the card-detail overlay.
 *
 * Kept as a small composable so the `BoxWithConstraints` width measurement and the routed `when`
 * stay isolated from the lifecycle effects above.
 *
 * @param appStore the root app store (Profile/Settings read root models).
 * @param accountStore the active account store.
 * @param route the active route.
 * @param showCardDetail whether the card-detail overlay should render (open or composing a card).
 * @param onOpenSwitcher opens the account-switcher overlay.
 */
@Composable
private fun BoxWithConstraintsRouting(
    appStore: Store<ModelState>,
    accountStore: Store<ModelState>,
    route: Route,
    showCardDetail: Boolean,
    onOpenSwitcher: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wsc = widthSizeClass(maxWidth)
        AdaptiveNav(
            sizeClass = wsc,
            currentRoute = route,
            onNavigate = { accountStore.dispatch(Navigate(it)) },
            header = { SwitcherAvatarButton(appStore = appStore, onOpenSwitcher = onOpenSwitcher) },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (route) {
                    is Route.BoardList -> BoardListScreen(accountStore)
                    is Route.Board -> BoardScreen(accountStore)
                    Route.Profile -> ProfileScreen(accountStore, appStore)
                    Route.Settings -> SettingsScreen(appStore)
                }
                if (showCardDetail) CardDetailScreen(accountStore)
            }
        }
    }
}

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
 * Drives the board lifecycle for the active account: entering [Route.Board] loads the board and
 * starts the bot; leaving (route changes away from a board) dispatches [BoardClosed] and stops the
 * bot, releasing the board slices + bot coroutine. Keyed on `(activeId, route)` so a route change
 * fires the `DisposableEffect` `onDispose` for the previous board before the new one runs.
 *
 * @param accountStore the active account store.
 * @param registry the per-account registry (bot start/stop).
 * @param activeId the active account.
 * @param route the active route.
 */
@Composable
private fun BoardLifecycleEffect(
    accountStore: Store<ModelState>,
    registry: AccountRegistry,
    activeId: AccountId,
    route: Route,
) {
    DisposableEffect(activeId, route) {
        if (route is Route.Board) {
            accountStore.dispatch(LoadBoardRequested(route.boardId))
            registry.startBot(activeId, rngSeed = 0L)
        }
        onDispose {
            if (route is Route.Board) {
                accountStore.dispatch(BoardClosed)
                registry.stopBot(activeId)
            }
        }
    }
}

/**
 * Periodic sync: while a board is open, ticks [Refresh] every `syncIntervalMs` (Rule F: >= 10 s,
 * cancelled when leaving the board). Flipping the fake service online also kicks an immediate
 * [Refresh] to drain the pending queue.
 *
 * @param appStore the root app store, read for the live `syncIntervalMs` / `online` knobs.
 * @param accountStore the active account store (the [Refresh] target).
 * @param route the active route; the loop only runs for [Route.Board].
 */
@Composable
private fun PeriodicSyncEffect(appStore: Store<ModelState>, accountStore: Store<ModelState>, route: Route) {
    val online by rememberStableStore(appStore).value.fieldStateOf(AppSettingsModel::class) { it.fakeService.online }
    val intervalMs by rememberStableStore(appStore).value
        .fieldStateOf(AppSettingsModel::class) { it.fakeService.syncIntervalMs }

    LaunchedEffect(route, intervalMs) {
        if (route is Route.Board) {
            val tick = intervalMs.coerceAtLeast(MIN_SYNC_INTERVAL_MS).toLong()
            while (isActive) {
                delay(tick)
                accountStore.dispatch(Refresh)
            }
        }
    }

    // Coming back online kicks an immediate sync so the queue drains promptly.
    LaunchedEffect(online, route) {
        if (online && route is Route.Board) accountStore.dispatch(Refresh)
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
}
