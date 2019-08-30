package org.reduxkotlin.examples.counter

import ch.tutteli.atrium.api.cc.en_GB.toBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import ch.tutteli.atrium.verbs.expect


object CounterSpek : Spek({

    describe("reducers") {
        describe("counter") {
            it("should handle INCREMENT action") {
                expect(reducer(1, Increment())).toBe(2)
            }

            it("should handle DECREMENT action") {
                expect(reducer(1, Decrement())).toBe(0)
            }

            it("should ignore unknown actions") {
                expect(reducer(1, Any())).toBe(1)
            }
        }
    }
})
