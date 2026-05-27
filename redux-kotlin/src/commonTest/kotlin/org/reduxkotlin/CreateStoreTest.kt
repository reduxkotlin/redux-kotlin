package org.reduxkotlin

import test.TodoApp
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateStoreTest {
    @Test
    fun passesTheInitialState() {
        val store = createStore(
            TodoApp.todoReducer,
            TodoApp.TodoState(
                listOf(
                    TodoApp.Todo(
                        id = "1",
                        text = "Hello",
                    ),
                ),
            ),
        )

        assertEquals(
            store.getState(),
            TodoApp.TodoState(
                listOf(
                    TodoApp.Todo(
                        id = "1",
                        text = "Hello",
                    ),
                ),
            ),
        )
    }

    @Test
    fun appliesTheReducerToThePreviousState() {
        val store = createStore(TodoApp.todoReducer, TodoApp.TodoState())
        assertEquals(store.getState(), TodoApp.TodoState())

        store.dispatch(Any())
        assertEquals(store.getState(), TodoApp.TodoState())

        store.dispatch(TodoApp.AddTodo("1", "Hello"))
        assertEquals(
            store.getState(),
            TodoApp.TodoState(
                listOf(
                    TodoApp.Todo(
                        id = "1",
                        text = "Hello",
                    ),
                ),
            ),
        )

        // TODO are ids autoincrement?
        store.dispatch(TodoApp.AddTodo("2", "World"))
        assertEquals(
            store.getState(),
            TodoApp.TodoState(
                listOf(
                    TodoApp.Todo(
                        id = "1",
                        text = "Hello",
                    ),
                    TodoApp.Todo(
                        id = "2",
                        text = "World",
                    ),
                ),
            ),
        )
    }

    @Test
    fun appliesTheReducerToTheInitialState() {
        val store = createStore(
            TodoApp.todoReducer,
            TodoApp.TodoState(
                listOf(
                    TodoApp.Todo(
                        id = "1",
                        text = "Hello",
                    ),
                ),
            ),
        )
        assertEquals(
            store.getState(),
            TodoApp.TodoState(
                listOf(
                    TodoApp.Todo(
                        id = "1",
                        text = "Hello",
                    ),
                ),
            ),
        )

        store.dispatch(Any())
        assertEquals(
            store.getState(),
            TodoApp.TodoState(
                listOf(
                    TodoApp.Todo(
                        id = "1",
                        text = "Hello",
                    ),
                ),
            ),
        )

        store.dispatch(TodoApp.AddTodo("2", "World"))
        assertEquals(
            store.getState(),
            TodoApp.TodoState(
                listOf(
                    TodoApp.Todo(
                        id = "1",
                        text = "Hello",
                    ),
                    TodoApp.Todo(
                        id = "2",
                        text = "World",
                    ),
                ),
            ),
        )
    }
}
