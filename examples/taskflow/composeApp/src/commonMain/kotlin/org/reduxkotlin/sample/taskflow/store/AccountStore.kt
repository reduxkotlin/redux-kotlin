package org.reduxkotlin.sample.taskflow.store

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.reduxkotlin.Store
import org.reduxkotlin.bundle.createConcurrentModelStore
import org.reduxkotlin.compose
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.bridge.BridgeConfig
import org.reduxkotlin.devtools.bridge.BridgeOutput
import org.reduxkotlin.devtools.devTools
import org.reduxkotlin.devtools.devToolsMiddleware
import org.reduxkotlin.devtools.named
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.routing.RoutingBuilder
import org.reduxkotlin.sample.taskflow.action.AddCard
import org.reduxkotlin.sample.taskflow.action.AddColumn
import org.reduxkotlin.sample.taskflow.action.Back
import org.reduxkotlin.sample.taskflow.action.BoardClosed
import org.reduxkotlin.sample.taskflow.action.BoardRestored
import org.reduxkotlin.sample.taskflow.action.BotAddedCard
import org.reduxkotlin.sample.taskflow.action.BotMovedCard
import org.reduxkotlin.sample.taskflow.action.CancelCreateCard
import org.reduxkotlin.sample.taskflow.action.CardMoveRequested
import org.reduxkotlin.sample.taskflow.action.CardOpFailed
import org.reduxkotlin.sample.taskflow.action.CardOpSucceeded
import org.reduxkotlin.sample.taskflow.action.CloseCard
import org.reduxkotlin.sample.taskflow.action.CreateBoard
import org.reduxkotlin.sample.taskflow.action.DeleteCard
import org.reduxkotlin.sample.taskflow.action.EditCard
import org.reduxkotlin.sample.taskflow.action.EditProfile
import org.reduxkotlin.sample.taskflow.action.EnterEditMode
import org.reduxkotlin.sample.taskflow.action.LoadBoardListSucceeded
import org.reduxkotlin.sample.taskflow.action.LoadBoardSucceeded
import org.reduxkotlin.sample.taskflow.action.Navigate
import org.reduxkotlin.sample.taskflow.action.OpenCard
import org.reduxkotlin.sample.taskflow.action.PushUndo
import org.reduxkotlin.sample.taskflow.action.RecordActivity
import org.reduxkotlin.sample.taskflow.action.SetFilterAssignee
import org.reduxkotlin.sample.taskflow.action.SetFilterQuery
import org.reduxkotlin.sample.taskflow.action.SetUndoModel
import org.reduxkotlin.sample.taskflow.action.StartCreateCard
import org.reduxkotlin.sample.taskflow.action.SyncStatusChanged
import org.reduxkotlin.sample.taskflow.action.ToggleFilterLabel
import org.reduxkotlin.sample.taskflow.data.SeedData
import org.reduxkotlin.sample.taskflow.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.data.remote.FakeRemoteApi
import org.reduxkotlin.sample.taskflow.data.remote.RemoteApi
import org.reduxkotlin.sample.taskflow.data.sync.SyncRepository
import org.reduxkotlin.sample.taskflow.middleware.activityLoggerMiddleware
import org.reduxkotlin.sample.taskflow.middleware.effectsMiddleware
import org.reduxkotlin.sample.taskflow.middleware.undoMiddleware
import org.reduxkotlin.sample.taskflow.model.AccountDetail
import org.reduxkotlin.sample.taskflow.model.AccountId
import org.reduxkotlin.sample.taskflow.model.AccountSummary
import org.reduxkotlin.sample.taskflow.model.ActivityModel
import org.reduxkotlin.sample.taskflow.model.AppSettingsModel
import org.reduxkotlin.sample.taskflow.model.BoardListModel
import org.reduxkotlin.sample.taskflow.model.BoardModel
import org.reduxkotlin.sample.taskflow.model.CollaboratorsModel
import org.reduxkotlin.sample.taskflow.model.FilterModel
import org.reduxkotlin.sample.taskflow.model.NavModel
import org.reduxkotlin.sample.taskflow.model.SessionModel
import org.reduxkotlin.sample.taskflow.model.SyncModel
import org.reduxkotlin.sample.taskflow.model.UndoModel
import org.reduxkotlin.sample.taskflow.platform.mainNotificationContext
import org.reduxkotlin.sample.taskflow.reducer.activityReducer
import org.reduxkotlin.sample.taskflow.reducer.boardListReducer
import org.reduxkotlin.sample.taskflow.reducer.boardReducer
import org.reduxkotlin.sample.taskflow.reducer.collaboratorsReducer
import org.reduxkotlin.sample.taskflow.reducer.filterReducer
import org.reduxkotlin.sample.taskflow.reducer.navReducer
import org.reduxkotlin.sample.taskflow.reducer.sessionReducer
import org.reduxkotlin.sample.taskflow.reducer.syncReducer
import org.reduxkotlin.sample.taskflow.reducer.undoModelReducer

/**
 * Owns one account's running store plus everything bound to that account's lifetime.
 *
 * Held outside the [org.reduxkotlin.registry.StoreRegistry] (which stores only the bare
 * [Store]) so the per-account [scope], [syncRepo], [remoteApi] and bot [botJob] can be torn down
 * when the account is removed. Cancelling [scope] cancels every effect/sync/bot coroutine the
 * account launched.
 *
 * @property store the per-account [ModelState] store.
 * @property scope the per-account background scope (effects, sync, bot all launch on it).
 * @property syncRepo the local-first data gateway feeding the effects middleware.
 * @property remoteApi the account's isolated fake backend.
 * @property detail the seed identity/collaborators this account was built from.
 * @property botJob the running bot coroutine, or `null` when the bot is stopped.
 * @property bridgeOutput the live [BridgeOutput] wired to this account's DevTools session, or
 *   `null` when no DevTools session was active at creation time. Must be stopped in [AccountRegistry.remove].
 * @property devtoolsId the DevTools session id used when registering this account's store, matching
 *   `DevToolsConfig.instanceId ?: DevToolsConfig.name`. Used by [AccountRegistry.remove] to call
 *   [DevToolsHub.removeSession].
 */
public class AccountStoreHandle(
    public val store: Store<ModelState>,
    public val scope: CoroutineScope,
    public val syncRepo: SyncRepository,
    public val remoteApi: RemoteApi,
    public val detail: AccountDetail,
    public var botJob: Job? = null,
    internal val bridgeOutput: BridgeOutput? = null,
    internal val devtoolsId: String? = null,
)

/**
 * Builds a fully wired per-account store: a concurrent [ModelState] store holding the account's
 * board-side models, behind the per-account effects / undo / activity middleware, fed by a
 * per-account [SyncRepository] over an isolated [FakeRemoteApi].
 *
 * The [block] declares every model slot up front (so [ModelState.get] never misses) and routes
 * each action to the matching pure reducer by its EXACT leaf class. The same handler (e.g.
 * `boardReducer`) is registered on the slots that need it; the routing layer fires each registered
 * handler for its action and leaves every other slot untouched.
 *
 * Threading (Rule E): all repository / bot work runs on the account [scope] (off-main); subscriber
 * callbacks marshal to the main thread via [notificationContext]. The effects collectors are
 * started eagerly here (by dispatching a benign warm-up action) so a reject/status from the very
 * first real mutation cannot race the lazy first-dispatch subscription.
 *
 * @param detail the seed identity + collaborators for this account.
 * @param rootStore the root app store, read only for live [AppSettingsModel.fakeService] settings.
 * @param localStore the durable offline cache (shared across accounts; queue/cursor are per-account).
 * @param notificationContext where subscriber callbacks run (default: platform main thread).
 * @param rngSeed seeds the account's [FakeRemoteApi] RNG for reproducible latency/failure rolls.
 * @param scope the per-account background scope effects/sync/bot launch on (default: a fresh
 *   [SupervisorJob] on [Dispatchers.Default]). Tests inject a test-scheduler scope so the whole
 *   effect/sync chain runs under virtual time.
 * @return the [AccountStoreHandle] owning the store + its per-account lifetime resources.
 */
public fun createAccountStore(
    detail: AccountDetail,
    rootStore: Store<ModelState>,
    localStore: LocalStore,
    notificationContext: NotificationContext = mainNotificationContext(),
    rngSeed: Long = 0L,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): AccountStoreHandle {
    val remoteApi = FakeRemoteApi(
        seededAccounts = SeedData.seededAccounts(),
        config = { rootStore.getModel<AppSettingsModel>().fakeService },
        rng = kotlin.random.Random(rngSeed),
    )
    val syncRepo = SyncRepository(localStore, remoteApi, scope, detail.accountId)
    val effects = effectsMiddleware(syncRepo, scope)

    // Redux DevTools: [devTools] records actions + state into the in-app hub session named
    // "TaskFlow"; [devToolsMiddleware] is a drop-in for applyMiddleware that ALSO captures the
    // named middleware pipeline for the drawer. The in-app drawer (App.kt) targets this fixed
    // session id — a single active account means no id collision.
    val devCfg = DevToolsConfig(name = "TaskFlow")
    val pipelineEnhancer = devToolsMiddleware(
        devCfg,
        named("activityLogger", activityLoggerMiddleware()),
        named("undo", undoMiddleware()),
        named("effects", effects),
    )
    val store = createConcurrentModelStore(
        notificationContext = notificationContext,
        enhancer = compose(devTools(devCfg), pipelineEnhancer),
    ) { declareAccountModels(detail) }

    // Eagerly trigger the effects middleware's first invocation so its reject/status collectors are
    // attached before any real mutation; the warm-up action matches no handler (a routing no-op).
    store.dispatch(EffectsWarmUp)

    val devtoolsId = devCfg.instanceId ?: devCfg.name
    val bridge = BridgeOutput(
        BridgeConfig(clientId = "taskflow", clientLabel = "TaskFlow"),
        logger = { println("[rk-bridge account] $it") },
    )
    DevToolsHub.session(devtoolsId)?.let { session ->
        bridge.start(session)
        println("[rk-bridge account] started against session '${session.id}'")
    } ?: println("[rk-bridge account] NO session '$devtoolsId' in hub — bridge not started")

    return AccountStoreHandle(
        store = store,
        scope = scope,
        syncRepo = syncRepo,
        remoteApi = remoteApi,
        detail = detail,
        bridgeOutput = bridge,
        devtoolsId = devtoolsId,
    )
}

/**
 * Declares every per-account model slot up front and routes each action to its pure reducer by EXACT
 * leaf class. Slots that share a reducer (e.g. `boardReducer`) register it on each; the routing layer
 * fires each registered handler for its action and leaves every other slot untouched. [selfId][AccountDetail.accountId]
 * is captured for the reducers that need the owning account.
 */
private fun RoutingBuilder.declareAccountModels(detail: AccountDetail) {
    val selfId = detail.accountId
    model(SessionModel(accountId = selfId, bio = null)) {
        on<EditProfile> { s, a -> sessionReducer(s, a) }
    }
    model(NavModel()) {
        on<Navigate> { s, a -> navReducer(s, a) }
        on<Back> { s, a -> navReducer(s, a) }
        on<EnterEditMode> { s, a -> navReducer(s, a) }
        on<OpenCard> { s, a -> navReducer(s, a) }
        on<CloseCard> { s, a -> navReducer(s, a) }
        on<StartCreateCard> { s, a -> navReducer(s, a) }
        on<CancelCreateCard> { s, a -> navReducer(s, a) }
    }
    model(BoardListModel()) {
        on<LoadBoardListSucceeded> { s, a -> boardListReducer(s, a) }
        on<CreateBoard> { s, a -> boardListReducer(s, a) }
    }
    model(CollaboratorsModel(seedCollaborators(detail))) {
        on<EditProfile> { s, a -> collaboratorsReducer(s, a, selfId) }
    }
    model(BoardModel()) {
        on<LoadBoardSucceeded> { s, a -> boardReducer(s, a, selfId) }
        on<CardMoveRequested> { s, a -> boardReducer(s, a, selfId) }
        on<AddCard> { s, a -> boardReducer(s, a, selfId) }
        on<AddColumn> { s, a -> boardReducer(s, a, selfId) }
        on<EditCard> { s, a -> boardReducer(s, a, selfId) }
        on<DeleteCard> { s, a -> boardReducer(s, a, selfId) }
        on<CardOpFailed> { s, a -> boardReducer(s, a, selfId) }
        on<BotMovedCard> { s, a -> boardReducer(s, a, selfId) }
        on<BotAddedCard> { s, a -> boardReducer(s, a, selfId) }
        on<BoardClosed> { s, a -> boardReducer(s, a, selfId) }
        on<BoardRestored> { s, a -> boardReducer(s, a, selfId) }
    }
    model(FilterModel()) {
        on<SetFilterQuery> { s, a -> filterReducer(s, a) }
        on<SetFilterAssignee> { s, a -> filterReducer(s, a) }
        on<ToggleFilterLabel> { s, a -> filterReducer(s, a) }
        on<BoardClosed> { s, a -> filterReducer(s, a) }
    }
    model(UndoModel()) {
        on<PushUndo> { s, a -> undoModelReducer(s, a) }
        on<SetUndoModel> { s, a -> undoModelReducer(s, a) }
        on<BoardClosed> { s, a -> undoModelReducer(s, a) }
    }
    model(SyncModel()) {
        on<CardMoveRequested> { s, a -> syncReducer(s, a) }
        on<AddCard> { s, a -> syncReducer(s, a) }
        on<EditCard> { s, a -> syncReducer(s, a) }
        on<DeleteCard> { s, a -> syncReducer(s, a) }
        on<CardOpSucceeded> { s, a -> syncReducer(s, a) }
        on<CardOpFailed> { s, a -> syncReducer(s, a) }
        on<SyncStatusChanged> { s, a -> syncReducer(s, a) }
        on<BoardClosed> { s, a -> syncReducer(s, a) }
    }
    model(ActivityModel()) {
        on<RecordActivity> { s, a -> activityReducer(s, a) }
    }
}

/**
 * Benign warm-up action: matches no model handler, so dispatching it is a routing no-op that only
 * drives the effects middleware's lazy first-invocation (which attaches its reject/status collectors).
 */
private object EffectsWarmUp

/**
 * Builds the initial [CollaboratorsModel] directory for [detail]: self + bot + every assignee
 * referenced on the account's seeded board.
 *
 * Drawing the assignees from [SeedData] (rather than only [AccountDetail.collaborators]) guarantees
 * the board screen can resolve every seeded card's avatar without reaching into the root store.
 *
 * @param detail the account whose collaborator directory is being seeded.
 * @return the self + bot + board-assignee summaries keyed by [AccountId].
 */
public fun seedCollaborators(detail: AccountDetail): PersistentMap<AccountId, AccountSummary> {
    val builder = persistentMapOf<AccountId, AccountSummary>().builder()
    builder[detail.self.id] = detail.self
    builder[SeedData.bot.id] = SeedData.bot
    detail.collaborators.forEach { (id, summary) -> builder[id] = summary }
    val seeded = SeedData.seededAccounts().firstOrNull { it.owner.id == detail.accountId }
    if (seeded != null) {
        // The board's collaborators (self + bot) cover every seeded card's assignee.
        seeded.collaborators.forEach { builder[it.id] = it }
    }
    return builder.build()
}
