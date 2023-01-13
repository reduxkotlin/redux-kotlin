package org.reduxkotlin.examples.todos

import kotlin.test.Test
import kotlin.test.assertEquals

class TodosReducerSpec {

  @Test
  fun shouldHandleAddToDo() {
    assertEquals(
      todosReducer(
        emptyList(),
        AddTodo(text = "Run the tests")
      ),
      listOf(
        Todo(
          text = "Run the tests",
          completed = false,
          id = 0
        )
      )
    )

    assertEquals(
      todosReducer(
        listOf(
          Todo(
            text = "Run the tests",
            completed = false,
            id = 0
          )
        ),
        AddTodo(text = "Use Redux")
      ),
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

    assertEquals(
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
      ),
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

  @Test
  fun shouldHandleToggleTodo() {
    assertEquals(
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
        ToggleTodo(index = 0)
      ),
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
