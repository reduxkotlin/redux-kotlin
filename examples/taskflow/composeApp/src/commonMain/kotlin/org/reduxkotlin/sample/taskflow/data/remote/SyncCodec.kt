package org.reduxkotlin.sample.taskflow.data.remote

import kotlinx.serialization.json.Json

/**
 * Json configuration used to (de)serialize [SyncOp] sync payloads.
 *
 * Unknown keys are ignored so the client tolerates fields added by newer backends; the
 * default class discriminator is used for the sealed [SyncOp] / [AttachmentDto] /
 * [InverseOpDto] hierarchies.
 */
private val syncJson: Json =
    Json {
        ignoreUnknownKeys = true
    }

/**
 * Encodes this [SyncOp] into a JSON string payload for transmission to the remote backend.
 */
public fun SyncOp.encodeToPayload(): String = syncJson.encodeToString(SyncOp.serializer(), this)

/**
 * Decodes a JSON string [payload] produced by [encodeToPayload] back into a [SyncOp].
 */
public fun decodeSyncOp(payload: String): SyncOp = syncJson.decodeFromString(SyncOp.serializer(), payload)
