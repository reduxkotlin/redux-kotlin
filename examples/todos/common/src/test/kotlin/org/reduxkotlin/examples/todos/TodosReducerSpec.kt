package org.reduxkotlin.examples.todos

import ch.tutteli.atrium.api.cc.en_GB.toBe
import ch.tutteli.atrium.verbs.expect
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TodosReducerSpec : Spek({
    describe("todos reducer") {
        it("should handle ADD_TODO") {
            expect(
                todosReducer(
                    listOf(), AddTodo(
                        text = "Run the tests"
                    )
                )
            ).toBe(
                listOf(
                    Todo(
                        text = "Run the tests",
                        completed = false
                    )
                )
            )

            expect(
                todosReducer(
                    listOf(
                        Todo(
                            text = "Run the tests",
                            completed = false
                        )
                    ),
                    AddTodo(
                        text = "Use Redux"
                    )
                )
            ).toBe(
                listOf(
                    Todo(
                        text = "Run the tests",
                        completed = false
                    ),
                    Todo(
                        text = "Use Redux",
                        completed = false
                    )
                )
            )

            expect(
                todosReducer(
                    listOf(
                        Todo(
                            text = "Run the tests",
                            completed = false
                        ),
                        Todo(
                            text = "Use Redux",
                            completed = false
                        )
                    ),
                    AddTodo(text = "Fix the tests")
                )
            ).toBe(
                listOf(
                    Todo(
                        text = "Run the tests",
                        completed = false
                    ),
                    Todo(
                        text = "Use Redux",
                        completed = false
                    ),
                    Todo(
                        text = "Fix the tests",
                        completed = false
                    )
                )
            )
        }

        it("should handle TOGGLE_TODO") {
            expect(
                todosReducer(
                    listOf(
                        Todo(
                            text = "Run the tests",
                            completed = false
                        ),
                        Todo(
                            text = "Use Redux",
                            completed = false
                        )
                    ), ToggleTodo(index = 0)
                )
            ).toBe(
                listOf(
                    Todo(
                        text = "Run the tests",
                        completed = true
                    ),
                    Todo(
                        text = "Use Redux",
                        completed = false
                    )
                )
            )
        }
    }
})
