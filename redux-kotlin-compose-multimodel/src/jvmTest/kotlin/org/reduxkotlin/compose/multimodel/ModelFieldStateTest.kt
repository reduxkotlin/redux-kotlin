package org.reduxkotlin.compose.multimodel

import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Test
import org.reduxkotlin.Store
import org.reduxkotlin.createStore
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.multimodel.combineModelReducers
import org.reduxkotlin.multimodel.modelReducer

private data class UserModel(val displayName: String = "")
private data class FeedModel(val items: List<String> = emptyList())

private data class SetDisplayName(val name: String)
private data class AppendFeed(val item: String)

@OptIn(ExperimentalTestApi::class)
class ModelFieldStateTest {

    private fun newStore(): Store<ModelState> = createStore(
        reducer = combineModelReducers(
            modelReducer<UserModel> { u, a ->
                if (a is SetDisplayName) u.copy(displayName = a.name) else u
            },
            modelReducer<FeedModel> { f, a ->
                if (a is AppendFeed) f.copy(items = f.items + a.item) else f
            },
        ),
        preloadedState = ModelState.of(UserModel("Ada"), FeedModel(listOf("a"))),
    )

    @Test
    fun reified_fieldState_renders_initial_then_updates() = runComposeUiTest {
        val store = newStore()
        setContent {
            val name by store.fieldState(UserModel::displayName)
            Text(text = "name=$name")
        }
        onAllNodesWithText("name=Ada").assertCountEquals(1)

        store.dispatch(SetDisplayName("Babbage"))
        waitForIdle()
        onAllNodesWithText("name=Babbage").assertCountEquals(1)
    }

    @Test
    fun unrelated_model_change_does_not_update_field_state() = runComposeUiTest {
        val store = newStore()
        setContent {
            val name by store.fieldState(UserModel::displayName)
            Text(text = "name=$name")
        }
        store.dispatch(AppendFeed("b"))
        waitForIdle()
        // UserModel didn't move; the binding stays at the initial value.
        onAllNodesWithText("name=Ada").assertCountEquals(1)
    }

    @Test
    fun kclass_fieldStateOf_shim_routes_to_correct_model() = runComposeUiTest {
        val store = newStore()
        setContent {
            val name by store.fieldStateOf(UserModel::class) { it.displayName }
            Text(text = "kclass=$name")
        }
        onAllNodesWithText("kclass=Ada").assertCountEquals(1)

        store.dispatch(SetDisplayName("Babbage"))
        waitForIdle()
        onAllNodesWithText("kclass=Babbage").assertCountEquals(1)
    }
}
