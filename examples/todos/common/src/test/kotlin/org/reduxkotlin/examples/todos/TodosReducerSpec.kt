package org.reduxkotlin.examples.todos

import ch.tutteli.atrium.api.cc.en_GB.toBe
import ch.tutteli.atrium.verbs.expect
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TodosReducerSpec : Spek({
    describe("todos reducer") {
        it("should handle AddToDo") {
            expect(
                todosReducer(
                    emptyList(),
                    AddTodo(text = "Run the tests")
                )
            ).toBe(
                listOf(
                    Todo(
                        text = "Run the tests",
                        completed = false,
                        id = 0
                    )
                )
            )

            expect(
                todosReducer(
                    listOf(
                        Todo(
                            text = "Run the tests",
                            completed = false,
                            id = 0
                        )
                    ),
                    AddTodo(text = "Use Redux")
                )
            ).toBe(
                listOf(
                    Todo(
                        text = "Run the tests",
                        completed = false,
                        id = 0
                    ),
                    Todo(
                        text = "Use Redux",
                        completed = false,
                        id = 1
                    )
                )
            )

            expect(
                todosReducer(
                    listOf(
                        Todo(
                            text = "Run the tests",
                            completed = false,
                            id = 0
                        ),
                        Todo(
                            text = "Use Redux",
                            completed = false,
                            id = 1
                        )
                    ),
                    AddTodo(text = "Fix the tests")
                )
            ).toBe(
                listOf(
                    Todo(
                        text = "Run the tests",
                        completed = false,
                        id = 0
                    ),
                    Todo(
                        text = "Use Redux",
                        completed = false,
                        id = 1
                    ),
                    Todo(
                        text = "Fix the tests",
                        completed = false,
                        id = 2
                    )
                )
            )
        }

        it("should handle ToggleTodo") {
            expect(
                todosReducer(
                    listOf(
                        Todo(
                            text = "Run the tests",
                            completed = false,
                            id = 0
                        ),
                        Todo(
                            text = "Use Redux",
                            completed = false,
                            id = 1
                        )
                    ), ToggleTodo(index = 0)
                )
            ).toBe(
                listOf(
                    Todo(
                        text = "Run the tests",
                        completed = true,
                        id = 0
                    ),
                    Todo(
                        text = "Use Redux",
                        completed = false,
                        id = 1
                    )
                )
            )
        }
    }
})
