package com.ahmadkharfan.androidstudiolite.data.remote.protocol

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import java.io.File
import kotlinx.serialization.decodeFromString

/**
 * Parses a single JSON [BuildEvent] frame from the control-plane WebSocket into the domain
 * [BuildEvent]. Returns null (rather than throwing) for blank frames, malformed JSON, or an unknown
 * `"type"` discriminator — per the protocol, consumers must ignore what they don't understand so
 * the stream can grow additively.
 */
class BuildEventParser(private val json: kotlinx.serialization.json.Json = RemoteJson) {

    /** Parses one frame into a domain [BuildEvent], resolving wire paths under [projectRoot]. */
    fun parse(frame: String, projectRoot: File): BuildEvent? {
        if (frame.isBlank()) return null
        val wire = runCatching { json.decodeFromString<WireBuildEvent>(frame) }.getOrNull() ?: return null
        return BuildEventMapper.toDomain(wire, projectRoot)
    }

    /** Parses an NDJSON block (log archive / batched frames), skipping any unparseable lines. */
    fun parseNdjson(block: String, projectRoot: File): List<BuildEvent> =
        block.lineSequence().mapNotNull { parse(it, projectRoot) }.toList()
}
