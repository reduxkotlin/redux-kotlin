package org.reduxkotlin

import ch.tutteli.atrium.api.fluent.en_GB.toBe
import ch.tutteli.atrium.creating.Assert
import ch.tutteli.atrium.domain.builders.migration.asAssert
import ch.tutteli.atrium.domain.builders.migration.asExpect
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

            expect(store.getState()).asExpect<TestState, Assert<TestState>>().toBe(
                TestState(
                    listOf(
                        Todo(
                            id = "1",
                            text = "Hello"
                        )
                    )
                )
            ).asAssert()
        }
        it("applies the reducer to the previous state") {
            val store = createStore(todos, TestState())
            expect(store.getState()).asExpect<TestState, Assert<TestState>>().toBe(TestState()).asAssert()

            store.dispatch(Any())
            expect(store.getState()).asExpect<TestState, Assert<TestState>>().toBe(TestState()).asAssert()

            store.dispatch(AddTodo("1", "Hello"))
            expect(store.getState()).asExpect<TestState, Assert<TestState>>().toBe(
                TestState(
                    listOf(
                        Todo(
                            id = "1",
                            text = "Hello"
                        )
                    )
                )
            ).asAssert()

            //TODO are ids autoincrement?
            store.dispatch(AddTodo("2", "World"))
            expect(store.getState()).asExpect<TestState, Assert<TestState>>().toBe(
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
            ).asAssert()
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
            expect(store.getState()).asExpect<TestState, Assert<TestState>>().toBe(
                TestState(
                    listOf(
                        Todo(
                            id = "1",
                            text = "Hello"
                        )
                    )
                )
            ).asAssert()

            store.dispatch(Any())
            expect(store.getState()).asExpect<TestState, Assert<TestState>>().toBe(
                TestState(
                    listOf(
                        Todo(
                            id = "1",
                            text = "Hello"
                        )
                    )
                )
            ).asAssert()

            store.dispatch(AddTodo("2", "World"))
            expect(store.getState()).asExpect<TestState, Assert<TestState>>().toBe(
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
            ).asAssert()
        }

    }
})
