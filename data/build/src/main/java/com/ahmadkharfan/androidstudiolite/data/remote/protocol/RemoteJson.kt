package com.ahmadkharfan.androidstudiolite.data.remote.protocol

import kotlinx.serialization.json.Json

val RemoteJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    classDiscriminator = "type"
    encodeDefaults = true
}
