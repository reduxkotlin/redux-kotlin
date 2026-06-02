package org.reduxkotlin.devtools.monitor

import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stress test: many bridge clients connect + feed frames concurrently (the documented root + account
 * use case, amplified). On the unsynchronized version this races [MonitorIngest.captures] and the
 * registry's `slots`/`_state`, producing HashMap corruption / lost StateFlow updates / exceptions.
 * With the lock it must register every store and retain every action.
 */
class MonitorIngestConcurrencyTest {

    @Test
    fun concurrent_connections_register_all_stores_and_lose_no_actions() {
        val threadCount = 16
        val actionsPerStore = 300
        val ingest = MonitorIngest()

        val start = CountDownLatch(1)
        val failures = CopyOnWriteArrayList<Throwable>()
        val done = AtomicInteger(0)

        val threads = (0 until threadCount).map { i ->
            thread(start = true) {
                try {
                    start.await()
                    val conn = ingest.openConnection()
                    conn.accept(
                        BridgeMessage.Hello(
                            protocolVersion = PROTOCOL_VERSION,
                            clientId = "client-$i",
                            clientLabel = "label-$i",
                            storeInstanceId = "store-$i",
                            storeName = "Store $i",
                            serializerTier = "reflection",
                        ),
                    )
                    for (a in 1..actionsPerStore) {
                        conn.accept(
                            BridgeMessage.Action(
                                actionId = a,
                                action = JsonPrimitive("act-$a"),
                                state = JsonPrimitive(a),
                                diff = emptyList(),
                                timestampMillis = a.toLong(),
                                isExcess = false,
                            ),
                        )
                    }
                    conn.close()
                } catch (t: Throwable) {
                    failures.add(t)
                } finally {
                    done.incrementAndGet()
                }
            }
        }

        start.countDown()
        threads.forEach { it.join(TimeUnit.SECONDS.toMillis(60)) }

        assertTrue(failures.isEmpty(), "Concurrent ingest threw: ${failures.joinToString { it.toString() }}")
        assertEquals(threadCount, done.get(), "All threads must finish")

        val stores = ingest.registry.state.value.stores
        assertEquals(threadCount, stores.size, "Every concurrently-connected store must be registered")

        // InAppModel bounds the retained log to maxActions (default 50); assert per-store retention
        // equals min(actionsPerStore, bound) so no update was silently lost to a race.
        val expectedPerStore = minOf(actionsPerStore, 50)
        val totalActions = stores.sumOf { it.state.actions.size }
        assertEquals(
            threadCount * expectedPerStore,
            totalActions,
            "Total retained actions across stores must match expected (no lost updates)",
        )
    }
}
