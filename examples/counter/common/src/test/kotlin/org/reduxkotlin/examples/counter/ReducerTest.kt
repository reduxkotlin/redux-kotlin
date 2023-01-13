package org.reduxkotlin.examples.counter

import kotlin.test.Test
import kotlin.test.expect

class ReducerTest {

  @Test
  fun shouldHandleINCREMENTAction() {
    expect(2) { reducer(1, Increment()) }
  }

  @Test
  fun shouldHandleDECREMENTAction() {
    expect(0) { reducer(1, Decrement()) }
  }

  @Test
  fun shouldIgnoreUnknownActions() {
    expect(1) { reducer(1, Any()) }
  }
}
