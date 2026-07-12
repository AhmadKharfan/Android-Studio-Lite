package com.ahmadkharfan.androidstudiolite.domain.model

enum class TerminalLineKind { Stdout, Stderr, Success }

data class TerminalOutputLine(val text: String, val kind: TerminalLineKind = TerminalLineKind.Stdout)
