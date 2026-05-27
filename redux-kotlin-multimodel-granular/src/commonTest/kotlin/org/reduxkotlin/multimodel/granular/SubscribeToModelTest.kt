package org.reduxkotlin.multimodel.granular

import org.reduxkotlin.createStore
import org.reduxkotlin.granular.subscribeFields
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.multimodel.combineModelReducers
import org.reduxkotlin.multimodel.modelReducer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class UserModel(val displayName: String = "")
private data class FeedModel(val items: List<String> = emptyList())
private data class UnregisteredModel(val value: Int = 0)

private data class SetDisplayName(val name: String)
private data class AppendFeedItem(val item: String)
private object UnrelatedAction

class SubscribeToModelTest {

    private fun newStore() = createStore(
        reducer = combineModelReducers(
            modelReducer<UserModel> { user, action ->
                if (action is SetDisplayName) user.copy(displayName = action.name) else user
            },
            modelReducer<FeedModel> { feed, action ->
                if (action is AppendFeedItem) feed.copy(items = feed.items + action.item) else feed
            },
        ),
        preloadedState = ModelState.of(UserModel("Ada"), FeedModel(listOf("a"))),
    )

    @Test
    fun reified_subscribeTo_fires_only_for_matching_model_field() {
        val store = newStore()
        val updates = mutableListOf<String>()
        // Drop the initial trigger fire so we're only measuring change-only events.
        val unsubscribe = store.subscribeTo(UserModel::displayName, triggerOnSubscribe = false) { _, new ->
            updates += new
        }

        store.dispatch(AppendFeedItem("b")) // unrelated model — must not fire
        store.dispatch(SetDisplayName("Babbage"))
        store.dispatch(SetDisplayName("Babbage")) // same value — must not fire (=== short-circuit)
        store.dispatch(SetDisplayName("Lovelace"))

        unsubscribe()
        assertEquals(listOf("Babbage", "Lovelace"), updates)
    }

    @Test
    fun reified_subscribeTo_fires_initial_value_when_trigger_default() {
        val store = newStore()
        val updates = mutableListOf<String>()
        store.subscribeTo(UserModel::displayName) { _, new -> updates += new }
        // First entry is the trigger-on-subscribe fire with the current value.
        assertEquals(listOf("Ada"), updates)
    }

    @Test
    fun dsl_on_mixes_fields_from_multiple_models() {
        val store = newStore()
        val nameUpdates = mutableListOf<String>()
        val feedUpdates = mutableListOf<List<String>>()

        store.subscribeFields {
            on(UserModel::displayName, triggerOnSubscribe = false) { _, new -> nameUpdates += new }
            on(FeedModel::items, triggerOnSubscribe = false) { _, new -> feedUpdates += new }
        }

        store.dispatch(SetDisplayName("Babbage"))
        store.dispatch(AppendFeedItem("b"))
        store.dispatch(UnrelatedAction) // === fast-path: no model changes, no listener fires

        assertEquals(listOf("Babbage"), nameUpdates)
        assertEquals(listOf(listOf("a", "b")), feedUpdates)
    }

    @Test
    fun unrelated_action_does_not_fire_any_listener() {
        val store = newStore()
        var fired = 0
        store.subscribeTo(UserModel::displayName, triggerOnSubscribe = false) { _, _ -> fired++ }
        store.subscribeTo(FeedModel::items, triggerOnSubscribe = false) { _, _ -> fired++ }

        store.dispatch(UnrelatedAction)
        assertEquals(0, fired)
    }

    @Test
    fun subscribeToModel_kclass_shim_routes_to_correct_model() {
        val store = newStore()
        val updates = mutableListOf<String>()
        val unsubscribe = store.subscribeToModel(
            modelClass = UserModel::class,
            selector = { it.displayName },
            triggerOnSubscribe = false,
            listener = { _, new -> updates += new },
        )

        store.dispatch(AppendFeedItem("b"))
        store.dispatch(SetDisplayName("Babbage"))
        unsubscribe()

        assertEquals(listOf("Babbage"), updates)
    }

    @Test
    fun onModel_kclass_dsl_shim_works_inside_subscribeFields() {
        val store = newStore()
        val updates = mutableListOf<String>()
        store.subscribeFields {
            onModel(UserModel::class, { it.displayName }, triggerOnSubscribe = false) { _, new ->
                updates += new
            }
        }
        store.dispatch(SetDisplayName("Babbage"))
        assertEquals(listOf("Babbage"), updates)
    }

    @Test
    fun unsubscribe_stops_further_notifications() {
        val store = newStore()
        val updates = mutableListOf<String>()
        val unsubscribe = store.subscribeTo(
            UserModel::displayName,
            triggerOnSubscribe = false,
        ) { _, new -> updates += new }

        store.dispatch(SetDisplayName("Babbage"))
        unsubscribe()
        store.dispatch(SetDisplayName("Lovelace"))

        assertEquals(listOf("Babbage"), updates)
    }

    @Test
    fun selector_throws_when_model_class_not_registered() {
        // Build a ModelState that DOES NOT have UnregisteredModel registered.
        val store = newStore()
        var captured: Throwable? = null

        store.subscribeFields(onSelectorError = { captured = it }) { scope ->
            scope.on(UnregisteredModel::value, triggerOnSubscribe = false) { _, _ -> }
        }

        assertTrue(captured is IllegalStateException, "expected IllegalStateException for unregistered model")
        assertEquals(true, captured!!.message!!.contains("UnregisteredModel"))
    }
}
