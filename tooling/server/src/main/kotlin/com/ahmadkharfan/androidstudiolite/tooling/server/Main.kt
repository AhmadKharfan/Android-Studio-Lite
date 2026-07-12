package com.ahmadkharfan.androidstudiolite.tooling.server

/**
 * Entry point of the on-device Gradle tooling server. The app extracts this module's fat jar from
 * its full-flavor assets and spawns it with the installed JDK; communication happens over stdio
 * using the :tooling:proto JSON-RPC protocol. Real implementation lands with Phase 4A (T10).
 */
fun main() {
    println("asl-tooling-server: skeleton — protocol not implemented yet")
}
