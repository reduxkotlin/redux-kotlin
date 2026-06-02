package org.reduxkotlin.sample.taskflow.store

import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.bridge.bridgeJson
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression: the bridge handshake must serialize via the explicit sealed
 * [BridgeMessage.serializer] — the form [org.reduxkotlin.devtools.bridge.BridgeConnection] uses — on
 * taskflow's runtime classpath. Guards against a serializer-resolution linkage regression for the
 * `Hello` frame.
 */
class BridgeHandshakeSerializeTest {
    @Test
    fun helloEncodesViaExplicitSerializer() {
        val hello = BridgeMessage.Hello(PROTOCOL_VERSION, "c", "C", "s", "s", "toString", null)
        val json = bridgeJson.encodeToString(BridgeMessage.serializer(), hello)
        assertTrue(json.contains("\"t\":\"hello\""), "expected hello discriminator, got: $json")
    }
}
