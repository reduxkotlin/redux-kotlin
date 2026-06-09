package org.reduxkotlin.compose.saveable

import androidx.compose.runtime.saveable.SaveableStateRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class RegistryRoundTripTest {

    @Test
    fun restore_rehydrates_a_fresh_store() {
        // Phase 1: a source store with non-initial state, register the provider.
        val source = newTestStore(TestState(tab = 5, query = "x"))
        val registry1 = SaveableStateRegistry(restoredValues = null) { true }
        wireSaveable(source, registry1, "k", testSaver)
        val saved = registry1.performSave()

        // Phase 2: a brand-new store (simulating process death) seeded with the
        // saved values. Restore must dispatch the rehydrate action.
        val fresh = newTestStore(TestState())
        val registry2 = SaveableStateRegistry(restoredValues = saved) { true }
        wireSaveable(fresh, registry2, "k", testSaver)

        assertEquals(TestState(tab = 5, query = "x"), fresh.state)
    }

    @Test
    fun cold_start_dispatches_nothing() {
        val store = newTestStore(TestState(tab = 9))
        var notified = 0
        val unsubscribe = store.subscribe { notified++ }

        val registry = SaveableStateRegistry(restoredValues = null) { true } // empty == cold start
        wireSaveable(store, registry, "k", testSaver)

        unsubscribe()
        assertEquals(0, notified)
        assertEquals(TestState(tab = 9), store.state)
    }

    @Test
    fun corrupt_snapshot_is_ignored_as_cold_start() {
        val store = newTestStore(TestState(tab = 9))
        var notified = 0
        val unsubscribe = store.subscribe { notified++ }

        val registry = SaveableStateRegistry(restoredValues = mapOf("k" to listOf("not-json"))) { true }
        wireSaveable(store, registry, "k", testSaver)

        unsubscribe()
        assertEquals(0, notified)
        assertEquals(TestState(tab = 9), store.state)
    }

    @Test
    fun failing_save_does_not_crash_perform_save() {
        val store = newTestStore(TestState(tab = 1))
        val throwingSaver = StateSaver<TestState, UiSnapshot>(
            serializer = UiSnapshot.serializer(),
            save = { error("boom") },
            restore = { RehydrateUi(it.tab, it.query) },
        )
        val registry = SaveableStateRegistry(restoredValues = null) { true }
        wireSaveable(store, registry, "k", throwingSaver)

        val saved = registry.performSave() // must not throw
        assertEquals(null, saved["k"]?.firstOrNull())
    }
}
