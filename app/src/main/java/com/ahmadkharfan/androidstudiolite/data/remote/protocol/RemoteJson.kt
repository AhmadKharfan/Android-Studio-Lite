package com.ahmadkharfan.androidstudiolite.data.remote.protocol

import kotlinx.serialization.json.Json

/**
 * The single kotlinx.serialization [Json] the remote layer uses. Configured to match the control
 * plane: unknown keys/types are ignored (forward-compatible), the discriminator is `"type"`, and
 * nulls are encoded so optional wire fields round-trip predictably.
 */
val RemoteJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    classDiscriminator = "type"
    encodeDefaults = true
}
