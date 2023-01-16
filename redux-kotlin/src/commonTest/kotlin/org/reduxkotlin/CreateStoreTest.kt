package org.reduxkotlin

import kotlin.test.Test
import kotlin.test.assertEquals

class CreateStoreTest {
    @Test
    fun passesTheInitialState() {
        val store = createStore(
            todos,
            TestState(
                listOf(
                    Todo(
                        id = "1",
                        text = "Hello"
                    )
                )
            )
        )

        assertEquals(
            store.getState(),
            TestState(
                listOf(
                    Todo(
                        id = "1",
                        text = "Hello"
                    )
                )
            )
        )
    }

    @Test
    fun appliesTheReducerToThePreviousState() {
        val store = createStore(todos, TestState())
        assertEquals(store.getState(), TestState())

        store.dispatch(Any())
        assertEquals(store.getState(), TestState())

        store.dispatch(AddTodo("1", "Hello"))
        assertEquals(
            store.getState(),
            TestState(
                listOf(
                    Todo(
                        id = "1",
                        text = "Hello"
                    )
                )
            )
        )

        // TODO are ids autoincrement?
        store.dispatch(AddTodo("2", "World"))
        assertEquals(
            store.getState(),
            TestState(
                listOf(
                    Todo(
                        id = "1",
                        text = "Hello"
                    ),
                    Todo(
                        id = "2",
                        text = "World"
                    )
                )
            )
        )
    }

    @Test
    fun appliesTheReducerToTheInitialState() {
        val store = createStore(
            todos,
            TestState(
                listOf(
                    Todo(
                        id = "1",
                        text = "Hello"
                    )
                )
            )
        )
        assertEquals(
            store.getState(),
            TestState(
                listOf(
                    Todo(
                        id = "1",
                        text = "Hello"
                    )
                )
            )
        )

        store.dispatch(Any())
        assertEquals(
            store.getState(),
            TestState(
                listOf(
                    Todo(
                        id = "1",
                        text = "Hello"
                    )
                )
            )
        )

        store.dispatch(AddTodo("2", "World"))
        assertEquals(
            store.getState(),
            TestState(
                listOf(
                    Todo(
                        id = "1",
                        text = "Hello"
                    ),
                    Todo(
                        id = "2",
                        text = "World"
                    )
                )
            )
        )
    }
}
