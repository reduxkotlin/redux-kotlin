package org.reduxkotlin

import ch.tutteli.atrium.api.cc.en_GB.toBe
import ch.tutteli.atrium.api.cc.en_GB.toThrow
import ch.tutteli.atrium.verbs.expect
import io.mockk.spyk
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals
import kotlin.test.expect

data class AddTodo(val id: String, val text: String)
data class Todo(val id: String, val text: String, val completed: Boolean = false)

data class TestState(val todos: List<Todo>)

fun todos(state:  TestState, action: Any): Any = when (action) {
        is AddTodo -> state.copy(todos = state.todos.plus(Todo(action.id, action.text, false)))
        else -> state
        /*
        case 'TOGGLE_TODO':
        return state.map(todo =>
        (todo.id === action.id)
        ? {...todo, completed: !todo.completed}
        : todo
        )
        default:
        return state

         */
    }

object ApplyMiddlewareSpec: Spek( {
    describe("middleware") {
        it("fails") {
            spyk(Any())
            expect(1).toBe(1)
            assertEquals(1, 1)
        }
    }

    describe("applyMiddleware") {
        it("warns when dispatching during middleware setup") {
            fun dispatchingMiddleware(getState: GetState<TestState>, next: Dispatcher, action: Any): Any {
                next(AddTodo("1", "Dont dispatch in middleware setup"))
                return { getState: GetState<TestState>, next: Dispatcher, action: Any -> next(action)}
            }
            applyMiddleware(::dispatchingMiddleware)(::createStore)(::todos)

            expect({
            applyMiddleware(dispatchingMiddleware)(createStore)(todos)}
            ).toThrow()
        })

        it("wraps dispatch method with middleware once", () => {
            fun test(spyOnMethods) {
                return methods => {
                spyOnMethods(methods)
                return next => action => next(action)
            }
            }

            const spy = jest.fn()
            const store = applyMiddleware(test(spy), thunk)(createStore)(reducers.todos)

            store.dispatch(addTodo("Use Redux"))
            store.dispatch(addTodo("Flux FTW!"))

            expect(spy.mock.calls.length).toEqual(1)

            expect(spy.mock.calls[0][0]).toHaveProperty("getState")
            expect(spy.mock.calls[0][0]).toHaveProperty("dispatch")

            expect(store.getState()).toEqual([
                { id: 1, text: "Use Redux" },
                { id: 2, text: "Flux FTW!" }
            ])
        })

    })