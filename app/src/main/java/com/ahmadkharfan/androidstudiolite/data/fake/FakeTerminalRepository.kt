package com.ahmadkharfan.androidstudiolite.data.fake

import com.ahmadkharfan.androidstudiolite.domain.model.TerminalLineKind
import com.ahmadkharfan.androidstudiolite.domain.model.TerminalOutputLine
import com.ahmadkharfan.androidstudiolite.domain.repository.TerminalRepository
import kotlinx.coroutines.delay

class FakeTerminalRepository : TerminalRepository {

    override suspend fun execute(command: String): List<TerminalOutputLine> {
        delay(350)
        val trimmed = command.trim()
        return when {
            trimmed.startsWith("cd") -> emptyList()
            trimmed == "pwd" -> listOf(TerminalOutputLine("/storage/emulated/0/projects/MyApplication"))
            trimmed == "ls" -> listOf(TerminalOutputLine("app  build.gradle.kts  gradle  gradlew  settings.gradle.kts"))
            trimmed.contains("tasks") && trimmed.contains("gradlew") -> listOf(
                TerminalOutputLine("assemble - Assembles the outputs of this project."),
                TerminalOutputLine("build - Assembles and tests this project."),
                TerminalOutputLine("clean - Deletes the build directory."),
                TerminalOutputLine("BUILD SUCCESSFUL in 2s", TerminalLineKind.Success),
            )
            trimmed.contains("gradlew") && (trimmed.contains("assemble") || trimmed.contains("build")) -> listOf(
                TerminalOutputLine("> Task :app:compileDebugKotlin"),
                TerminalOutputLine("> Task :app:mergeDebugResources"),
                TerminalOutputLine("BUILD SUCCESSFUL in 8s", TerminalLineKind.Success),
            )
            trimmed == "help" -> listOf(TerminalOutputLine("Available: ls, pwd, cd, clear, ./gradlew tasks, ./gradlew assembleDebug"))
            trimmed.isEmpty() -> emptyList()
            else -> listOf(TerminalOutputLine("bash: $trimmed: command not found", TerminalLineKind.Stderr))
        }
    }
}
