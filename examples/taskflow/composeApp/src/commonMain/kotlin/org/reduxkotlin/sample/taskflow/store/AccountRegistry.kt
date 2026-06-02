package org.reduxkotlin.sample.taskflow.store

import kotlinx.coroutines.cancel
import org.reduxkotlin.Store
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.registry.StoreRegistry
import org.reduxkotlin.sample.taskflow.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.middleware.startBot
import org.reduxkotlin.sample.taskflow.model.AccountDetail
import org.reduxkotlin.sample.taskflow.model.AccountId
import org.reduxkotlin.sample.taskflow.model.AppSettingsModel
import org.reduxkotlin.sample.taskflow.platform.mainNotificationContext

/**
 * Owns the live per-account stores, isolating each account's state, effects, sync and bot.
 *
 * The bare [Store]s live in a [StoreRegistry] (lock-free, atomic snapshot); the richer
 * [AccountStoreHandle]s — which also carry the per-account [kotlinx.coroutines.CoroutineScope],
 * [org.reduxkotlin.sample.taskflow.data.sync.SyncRepository] and bot [kotlinx.coroutines.Job] —
 * live in a parallel side map so [remove] can tear the account's coroutines down (the registry
 * only ever forgets a store, never disposes it).
 *
 * @param rootStore the root app store, forwarded to each account store for live settings reads.
 * @param localStore the durable offline cache shared across accounts (per-account queue/cursor).
 * @param notificationContext where each account store's subscriber callbacks run (default: main thread).
 */
public class AccountRegistry(
    private val rootStore: Store<ModelState>,
    private val localStore: LocalStore,
    private val notificationContext: NotificationContext = mainNotificationContext(),
) {
    private val registry = StoreRegistry<AccountId, ModelState>()
    private val handles = mutableMapOf<AccountId, AccountStoreHandle>()

    /**
     * Returns the existing handle for [id], or builds, registers and returns a new per-account store
     * from [detail]. The bare store is mirrored into the [StoreRegistry] so [get] / [store] can serve
     * it lock-free.
     *
     * @param id the account to fetch or create a store for.
     * @param detail the seed identity/collaborators used when a new store must be built.
     * @return the account's [AccountStoreHandle].
     */
    public fun getOrCreate(id: AccountId, detail: AccountDetail): AccountStoreHandle {
        handles[id]?.let { return it }
        val handle = createAccountStore(detail, rootStore, localStore, notificationContext)
        registry.getOrCreate(id) { handle.store }
        handles[id] = handle
        return handle
    }

    /**
     * Lock-free lookup of [id]'s bare store, or `null` if none is registered.
     *
     * @param id the account to look up.
     * @return the account's [Store], or `null`.
     */
    public fun get(id: AccountId): Store<ModelState>? = registry.get(id)

    /**
     * The [AccountStoreHandle] for [id], or `null` if the account has no live store.
     *
     * @param id the account to look up.
     * @return the account's handle, or `null`.
     */
    public fun handle(id: AccountId): AccountStoreHandle? = handles[id]

    /**
     * Convenience alias for [get]: the bare [Store] for [id], or `null`.
     *
     * @param id the account to look up.
     * @return the account's [Store], or `null`.
     */
    public fun store(id: AccountId): Store<ModelState>? = registry.get(id)

    /**
     * Tears down the account [id]: stops the bridge output and removes its DevTools session, cancels
     * its bot and its whole coroutine [scope][AccountStoreHandle.scope] (stopping every effect/sync
     * coroutine), forgets the handle, and removes the store from the registry.
     *
     * @param id the account to evict.
     */
    public fun remove(id: AccountId) {
        handles[id]?.let { handle ->
            handle.bridgeOutput?.stop()
            handle.devtoolsId?.let { DevToolsHub.removeSession(it) }
            handle.botJob?.cancel()
            handle.scope.cancel()
        }
        handles.remove(id)
        registry.remove(id)
    }

    /**
     * Starts (or restarts) the simulated collaborator for account [id] on its own scope, replacing any
     * existing bot job. No-op when [id] has no live store.
     *
     * @param id the account whose bot to start.
     * @param rngSeed seeds the bot's deterministic move RNG.
     */
    public fun startBot(id: AccountId, rngSeed: Long) {
        handles[id]?.let { handle ->
            handle.botJob = startBot(
                handle.scope,
                handle.store,
                { rootStore.getModel<AppSettingsModel>().fakeService },
                rngSeed,
            )
        }
    }

    /**
     * Stops the simulated collaborator for account [id] (cancels its bot job). No-op when [id] has no
     * live store or no running bot.
     *
     * @param id the account whose bot to stop.
     */
    public fun stopBot(id: AccountId) {
        handles[id]?.let { handle ->
            handle.botJob?.cancel()
            handle.botJob = null
        }
    }
}
