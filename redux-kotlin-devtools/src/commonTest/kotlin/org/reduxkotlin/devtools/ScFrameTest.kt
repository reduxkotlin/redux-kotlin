package org.reduxkotlin.devtools.socketcluster

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScFrameTest {
    @Test
    fun encodesHandshakeWithCid() {
        val text = ScFrame.encodeHandshake(cid = 1)
        assertEquals("""{"event":"#handshake","data":{},"cid":1}""", text)
    }

    @Test
    fun encodesInvokeWithStringData() {
        val text = ScFrame.encodeInvoke(event = "login", data = JsonPrimitive("master"), cid = 2)
        assertEquals("""{"event":"login","data":"master","cid":2}""", text)
    }

    @Test
    fun encodesSubscribe() {
        val text = ScFrame.encodeSubscribe(channel = "abc", cid = 3)
        assertEquals("""{"event":"#subscribe","data":{"channel":"abc"},"cid":3}""", text)
    }

    @Test
    fun encodesTransmitWithoutCid() {
        val data = JsonObject(mapOf("type" to JsonPrimitive("ACTION")))
        val text = ScFrame.encodeTransmit(event = "log", data = data)
        assertEquals("""{"event":"log","data":{"type":"ACTION"}}""", text)
    }

    @Test
    fun emptyStringFrameDecodesAsPing() {
        assertIs<ScInbound.Ping>(ScFrame.decode(""))
    }

    @Test
    fun encodesPongAsEmptyString() {
        assertEquals("", ScFrame.PONG)
    }

    @Test
    fun decodesRpcResponseByRid() {
        val frame = ScFrame.decode("""{"rid":2,"data":"channelXYZ"}""")
        val resp = assertIs<ScInbound.RpcResponse>(frame)
        assertEquals(2, resp.rid)
        assertEquals("channelXYZ", resp.data?.jsonPrimitive?.content)
        assertTrue(resp.error == null)
    }

    @Test
    fun decodesHandshakeAckExtractsSocketId() {
        val frame = ScFrame.decode("""{"rid":1,"data":{"id":"sock-9","isAuthenticated":false}}""")
        val resp = assertIs<ScInbound.RpcResponse>(frame)
        assertEquals("sock-9", resp.data?.jsonObject?.get("id")?.jsonPrimitive?.content)
    }

    @Test
    fun decodesChannelPublishMessage() {
        val raw = """{"event":"#publish","data":{"channel":"abc","data":{"type":"START"}}}"""
        val frame = ScFrame.decode(raw)
        val pub = assertIs<ScInbound.ChannelMessage>(frame)
        assertEquals("abc", pub.channel)
        assertEquals("START", pub.data.jsonObject["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun decodesUnknownFrameAsOther() {
        assertIs<ScInbound.Other>(ScFrame.decode("""{"event":"#removeAuthToken"}"""))
    }
}
