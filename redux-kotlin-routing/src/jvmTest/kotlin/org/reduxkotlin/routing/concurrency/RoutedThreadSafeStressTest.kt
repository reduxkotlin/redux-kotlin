package org.reduxkotlin.routing.concurrency

import org.reduxkotlin.routing.AddItem
import org.reduxkotlin.routing.CartModel
import org.reduxkotlin.routing.createModelStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutedThreadSafeStressTest {

    @Test
    fun externally_synchronized_concurrent_dispatch_does_not_lose_writes() {
        val store = createModelStore {
            model(CartModel()) {
                on<AddItem> { c, a -> c.copy(items = c.items + a.item) }
            }
        }
        val lock = Any()
        val threads = 8
        val perThread = 1000
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) {
            pool.submit {
                start.await()
                repeat(perThread) { synchronized(lock) { store.dispatch(AddItem("x")) } }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        pool.shutdown()
        assertEquals(threads * perThread, store.state.get<CartModel>().items.size)
    }
}
