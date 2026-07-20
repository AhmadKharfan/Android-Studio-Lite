package com.ahmadkharfan.androidstudiolite.data.remote.protocol

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import java.io.File
import kotlinx.serialization.decodeFromString

class BuildEventParser(private val json: kotlinx.serialization.json.Json = RemoteJson) {

    fun parse(frame: String, projectRoot: File): BuildEvent? {
        if (frame.isBlank()) return null
        val wire = runCatching { json.decodeFromString<WireBuildEvent>(frame) }.getOrNull() ?: return null
        return BuildEventMapper.toDomain(wire, projectRoot)
    }

    fun parseNdjson(block: String, projectRoot: File): List<BuildEvent> =
        block.lineSequence().mapNotNull { parse(it, projectRoot) }.toList()
}
