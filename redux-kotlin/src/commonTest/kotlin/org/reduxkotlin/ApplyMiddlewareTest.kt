package org.reduxkotlin

import test.TodoApp
import kotlin.test.Test
import kotlin.test.assertFails

class ApplyMiddlewareTest {
    @Test
    fun warnsWhenDispatchingDuringMiddlewareSetup() {
        fun dispatchingMiddleware(store: Store<TodoApp.TodoState>): (next: Dispatcher) -> (action: Any) -> Any {
            store.dispatch(TodoApp.AddTodo("1", "Dont dispatch in middleware setup"))
            return { next ->
                { action ->
                    {
                        next(action)
                    }
                }
            }
        }

        assertFails {
            val storeEnhancer: StoreEnhancer<TodoApp.TodoState> = applyMiddleware(::dispatchingMiddleware)
            createStore(TodoApp.todoReducer, TodoApp.TodoState(), storeEnhancer)
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
