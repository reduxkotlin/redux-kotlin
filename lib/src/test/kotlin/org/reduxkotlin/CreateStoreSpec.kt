package org.reduxkotlin

import ch.tutteli.atrium.api.cc.en_GB.toBe
import ch.tutteli.atrium.verbs.expect
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CreateStoreSpec : Spek({
    describe("createStore") {
        it("passes the initial state") {
            val store = createStore(
                todos, TestState(
                    listOf(
                        Todo(
                            id = "1",
                            text = "Hello"
                        )
                    )
                )
            )

            expect(store.getState()).toBe(
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
        it("applies the reducer to the previous state") {
            val store = createStore(todos, TestState())
            expect(store.getState()).toBe(TestState())

            store.dispatch(Any())
            expect(store.getState()).toBe(TestState())

            store.dispatch(AddTodo("1", "Hello"))
            expect(store.getState()).toBe(
                TestState(
                    listOf(
                        Todo(
                            id = "1",
                            text = "Hello"
                        )
                    )
                )
            )

            //TODO are ids autoincrement?
            store.dispatch(AddTodo("2", "World"))
            expect(store.getState()).toBe(
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

        it("applies the reducer to the initial state") {
            val store = createStore(
                todos, TestState(
                    listOf(
                        Todo(
                            id = "1",
                            text = "Hello"
                        )
                    )
                )
            )
            expect(store.getState()).toBe(
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
            expect(store.getState()).toBe(
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
            expect(store.getState()).toBe(
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
})
