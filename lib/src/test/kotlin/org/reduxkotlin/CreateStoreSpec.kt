package org.reduxkotlin

import ch.tutteli.atrium.verbs.expect
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CreateStoreSpec: Spek({
    describe("createStore") {
        it("passes the initial state") {
            val store = createStore(::todos, TestState(
                listOf(Todo(
                id = "1",
                text = "Hello"))

            expect(store.getState()).toEqual([
                {
                    id: 1,
                    text: "Hello"
                }
            ])
        })
    }
})