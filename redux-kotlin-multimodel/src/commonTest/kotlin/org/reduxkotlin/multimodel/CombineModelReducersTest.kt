package org.reduxkotlin.multimodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private data class CmrUser(val displayName: String = "")
private data class CmrFeed(val items: List<String> = emptyList())

private data class SetDisplayName(val name: String)
private data class AppendFeedItem(val item: String)
private object UnrelatedAction

class CombineModelReducersTest {

    private val userReducer = modelReducer<CmrUser> { user, action ->
        when (action) {
            is SetDisplayName -> user.copy(displayName = action.name)
            else -> user
        }
    }

    private val feedReducer = modelReducer<CmrFeed> { feed, action ->
        when (action) {
            is AppendFeedItem -> feed.copy(items = feed.items + action.item)
            else -> feed
        }
    }

    private val root = combineModelReducers(userReducer, feedReducer)

    @Test
    fun action_updates_only_matching_model() {
        val initial = ModelState.of(CmrUser("Ada"), CmrFeed(listOf("a")))
        val next = root(initial, SetDisplayName("Babbage"))

        assertEquals(CmrUser("Babbage"), next.get<CmrUser>())
        assertSame(initial.get<CmrFeed>(), next.get<CmrFeed>())
    }

    @Test
    fun unrelated_action_returns_same_modelstate_reference() {
        val initial = ModelState.of(CmrUser("Ada"), CmrFeed(listOf("a")))
        val next = root(initial, UnrelatedAction)
        // === fast-path: no model changed, ModelState identity preserved
        // so the granular subscription layer can short-circuit completely.
        assertSame(initial, next)
    }

    @Test
    fun changed_model_yields_new_modelstate_with_unchanged_siblings_shared() {
        val initial = ModelState.of(CmrUser("Ada"), CmrFeed(listOf("a")))
        val next = root(initial, AppendFeedItem("b"))

        // ModelState itself is a new instance (some model changed)…
        assertEquals(false, initial === next)
        // …but the sibling CmrUser slot is the same reference.
        assertSame(initial.get<CmrUser>(), next.get<CmrUser>())
        assertEquals(CmrFeed(listOf("a", "b")), next.get<CmrFeed>())
    }

    @Test
    fun duplicate_keys_throw() {
        val duplicateUser = modelReducer<CmrUser> { u, _ -> u }
        assertFailsWith<IllegalArgumentException> {
            combineModelReducers(userReducer, duplicateUser)
        }
    }

    @Test
    fun reducer_registered_for_unregistered_model_throws_at_dispatch() {
        // Only CmrUser is in the state — but combined reducer also covers CmrFeed.
        val initial = ModelState.of(CmrUser("Ada"))
        val error = assertFailsWith<IllegalStateException> {
            root(initial, UnrelatedAction)
        }
        assertEquals(true, error.message!!.contains("CmrFeed"))
    }

    @Test
    fun modelReducerOf_works_with_kclass_at_call_site() {
        val r = modelReducerOf(CmrUser::class) { user, action ->
            if (action is SetDisplayName) user.copy(displayName = action.name) else user
        }
        val combined = combineModelReducers(r)
        val initial = ModelState.of(CmrUser("Ada"))
        val next = combined(initial, SetDisplayName("Babbage"))
        assertEquals(CmrUser("Babbage"), next.get<CmrUser>())
    }
}
