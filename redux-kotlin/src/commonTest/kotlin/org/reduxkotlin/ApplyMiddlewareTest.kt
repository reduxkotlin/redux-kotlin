package org.reduxkotlin

import kotlin.test.Test
import kotlin.test.assertFails

class ApplyMiddlewareSpec {
  @Test
  fun warnsWhenDispatchingDuringMiddlewareSetup() {
    fun dispatchingMiddleware(store: Store<TestState>): (next: Dispatcher) -> (action: Any) -> Any {
      store.dispatch(AddTodo("1", "Dont dispatch in middleware setup"))
      return { next ->
        { action ->
          {
            next(action)
          }
        }
      }
    }

    assertFails {
      val storeEnhancer: StoreEnhancer<TestState> = applyMiddleware(::dispatchingMiddleware)
      createStore(todos, TestState(), storeEnhancer)
    }
  }

/*
  it("wraps dispatch method with middleware once") {
      fun test(spyOnMethods) {
          return methods => {
              spyOnMethods(methods)
              return next => action => next(action)
          }
      }

      val spy = jest.fn()
      val store = applyMiddleware (test(spy), thunk)(createStore)(reducers.todos)
      store.dispatch(AddTodo("Use Redux"))
      store.dispatch(AddTodo("Flux FTW!"))

      expect(spy.mock.calls.length).toBe(1)

      expect(spy.mock.calls[0][0]).toHaveProperty("getState")
      expect(spy.mock.calls[0][0]).toHaveProperty("dispatch")

      expect(store.getState()).toEqual([
          { id: 1, text: "Use Redux" },
          { id: 2, text: "Flux FTW!" }
      ])
  })

   */
}

/************** Test Reducer & actions - tobe moved into example app *********/

data class AddTodo(val id: String, val text: String)
data class ToggleTodo(val id: String)
data class Todo(val id: String, val text: String, val completed: Boolean = false)

data class TestState(val todos: List<Todo> = listOf())

val todos = { state: TestState, action: Any ->
  when (action) {
    is AddTodo -> state.copy(todos = state.todos.plus(Todo(action.id, action.text, false)))
    is ToggleTodo -> state.copy(
      todos = state.todos.map {
        if (it.id == action.id) {
          it.copy(completed = !it.completed)
        } else {
          it
        }
      }
    )
    else -> state
  }
}
