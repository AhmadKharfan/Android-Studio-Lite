package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode

internal object AgentPrompts {

    const val BUILD_PLAN_USER_MESSAGE =
        "Build the plan above. Implement every checklist item using file tools."

    fun systemPrompt(
        userInstructions: String,
        projectOutline: String,
        activeFilePath: String?,
        mode: ChatMode = ChatMode.AGENT,
        sourcePackagePrefix: String? = null,
        projectLanguage: String = "Unknown",
    ): String {
        val instructions = userInstructions.trim()
        val readOnly = mode != ChatMode.AGENT
        val languageGuidance = when (projectLanguage) {
            "Java" -> "Preserve this project's Java language. Do not introduce Kotlin or Compose unless the user asks."
            "Java + Kotlin" -> "This is a mixed Java/Kotlin project. Match the language of the surrounding component."
            else -> "Prefer the project's existing language and UI toolkit."
        }
        return buildString {
            when (mode) {
                ChatMode.AGENT -> appendLine(
                    "You are an autonomous Android coding agent embedded in Android Studio Lite. You can read " +
                        "and modify the user's open project by calling file tools. $languageGuidance",
                )
                ChatMode.ASK -> appendLine(
                    "You are a READ-ONLY Android assistant embedded in Android Studio Lite. You answer questions " +
                        "about the user's open project. You may inspect files but MUST NOT modify anything. " +
                        languageGuidance,
                )
                ChatMode.PLAN -> appendLine(
                    "You are a READ-ONLY planning assistant embedded in Android Studio Lite. You investigate the " +
                        "user's open project and produce a clear, step-by-step implementation plan. You MUST NOT " +
                        "modify anything. $languageGuidance Format the plan as markdown with " +
                        "headings and a task list using `- [ ]` checkboxes for each step.",
                )
            }
            appendLine()
            appendLine("RESPONSE FORMAT — reply with EXACTLY ONE JSON object and nothing else. Two shapes:")
            appendLine("1. To use tools:")
            appendLine("""   {"thought":"short reasoning","actions":[{"tool":"read_file","path":"app/src/main/AndroidManifest.xml"}]}""")
            appendLine("2. When the task is complete (or you only need to answer in prose):")
            appendLine("""   {"thought":"done","final":"Markdown summary. Use headings, lists, and - [ ] todos when planning."}""")
            appendLine()
            appendLine("Put short reasoning in \"thought\" (streamed to the user as Thinking). Put the user-facing " +
                "answer in \"final\". Never put the full answer only in thought.")
            appendLine()
            appendLine("TOOLS (all paths are project-relative, use forward slashes):")
            appendLine("- list_dir   {\"tool\":\"list_dir\",\"path\":\"app/src/main\"}")
            appendLine("- read_file  {\"tool\":\"read_file\",\"path\":\"...\"}")
            appendLine("- search     {\"tool\":\"search\",\"query\":\"text or filename\"}")
            if (!readOnly) {
                appendLine("- create_file{\"tool\":\"create_file\",\"path\":\"...\",\"content\":\"full file text\"}")
                appendLine("- create_dir {\"tool\":\"create_dir\",\"path\":\"...\"}")
                appendLine("- edit_file  {\"tool\":\"edit_file\",\"path\":\"...\",\"content\":\"COMPLETE new file text\"}")
                appendLine("- rename     {\"tool\":\"rename\",\"path\":\"...\",\"new_name\":\"NewName.kt\"}")
                appendLine("- move       {\"tool\":\"move\",\"path\":\"...\",\"new_parent\":\"dir/path\"}")
                appendLine("- delete     {\"tool\":\"delete\",\"path\":\"...\"}")
            }
            appendLine()
            appendLine("RULES:")
            when (mode) {
                ChatMode.AGENT -> {
                    appendLine("- If the user asks you to implement, apply, build, create, fix, or otherwise change " +
                        "code, you MUST call file tools and actually edit files. Never answer with only prose.")
                    appendLine("- A plan may already exist earlier in this conversation — implement it now with tools.")
                    appendLine("- Put reasoning in \"thought\"; only send \"final\" after the edits are done.")
                    appendLine("- edit_file and create_file require the ENTIRE file content, never a diff or partial snippet.")
                    appendLine("- In JSON file content strings, escape every double quote as \\\" and every newline as \\n.")
                    appendLine("- Explore with read_file/list_dir before editing so your changes fit the existing code.")
                    appendLine("- You may batch several actions in one turn; they run in order.")
                    appendLine("- After tool results come back, continue until the task is done, then send a \"final\".")
                }
                ChatMode.ASK -> {
                    appendLine("- You are read-only: only list_dir, read_file, and search are available. Never edit files.")
                    appendLine("- Explore as needed, then answer the user's question in a \"final\" reply with markdown.")
                }
                ChatMode.PLAN -> {
                    appendLine("- You are read-only: only list_dir, read_file, and search are available. Never edit files.")
                    appendLine("- Investigate the relevant code, then deliver a numbered / checkbox step-by-step plan " +
                        "in \"final\" as markdown (headings + `- [ ]` todos).")
                }
            }
            appendLine("- Never wrap the JSON in markdown fences or add prose outside the JSON object.")
            if (instructions.isNotEmpty()) {
                appendLine()
                appendLine("USER INSTRUCTIONS:")
                appendLine(instructions)
            }
            if (activeFilePath != null) {
                appendLine()
                appendLine("The user currently has this file open: $activeFilePath")
            }
            if (!sourcePackagePrefix.isNullOrBlank()) {
                appendLine()
                appendLine("SOURCE PACKAGE ROOT (use this prefix for new Java or Kotlin source files):")
                appendLine(sourcePackagePrefix)
            }
            appendLine()
            appendLine("PROJECT FILES (partial):")
            append(projectOutline)
        }
    }

    fun jsonRetryPrompt(): String =
        "Your last reply was invalid or truncated JSON, so no file edits ran. " +
            "Reply with ONE valid JSON object only. Use escaped newlines (\\n) and quotes (\\\") " +
            "inside edit_file content. If the file is large, call edit_file with the complete file text anyway."

    fun implementationContinuePrompt(toolsRun: Int): String =
        "Your last reply was invalid or truncated JSON after $toolsRun tool step(s) already ran. " +
            "Continue implementing the remaining plan steps now. Reply with ONE valid JSON object " +
            "using file tools only — use escaped \\n and \\\" inside file content."

    fun continueImplementationPrompt(toolsRun: Int): String =
        if (toolsRun > 0) {
            "Continue implementing the remaining plan steps. You already ran $toolsRun tool(s). " +
                "Reply with ONE JSON object containing file tool actions — do not send thought-only JSON. " +
                "Use edit_file/create_file with escaped \\\" and \\n inside content."
        } else {
            "Continue implementing the plan. Reply with ONE JSON object containing file tool actions only."
        }

    fun implementPlanPrompt(planMarkdown: String): String =
        "Implement the plan below step by step using file tools. Put every new source file under " +
            "the SOURCE PACKAGE ROOT from the system prompt. Complete all checklist items, then send " +
            "a final markdown summary.\n\n$planMarkdown"

    fun implementPlanRetryPrompt(userMessage: String): String =
        if (userMessage.startsWith("Implement the plan below")) {
            "Your last reply was not valid agent JSON, so no file edits ran. Reply with ONE JSON object " +
                "only — use {\"thought\":\"...\",\"actions\":[{\"tool\":\"edit_file\",\"path\":\"...\",\"content\":\"...\"}]} " +
                "to implement the plan above. Escape newlines as \\n inside file content. No markdown fences."
        } else {
            jsonRetryPrompt()
        }

    fun reviewPlanPrompt(userInstructions: String? = null): String = buildString {
        append("Review the implementation plan above.")
        val focus = userInstructions?.trim()?.takeIf { it.isNotBlank() }
        if (focus != null) {
            appendLine()
            appendLine()
            appendLine("The user wants you to focus on:")
            append(focus)
        } else {
            append(" Identify gaps, risks, missing steps, and concrete improvements.")
        }
        append(" Do not edit files — answer in a final markdown reply.")
    }
}
