package com.ahmadkharfan.androidstudiolite.feature.editor.engine
enum class DiagnosticSeverity { Error, Warning, Info, Hint }
data class Diagnostic(
    val start: Int,
    val end: Int,
    val severity: DiagnosticSeverity,
    val message: String = "",
    val muted: Boolean = false,
    val code: String? = null,
)

object DiagnosticCodes {
    const val KT_SYNTAX = "kt.syntax"
    const val KT_UNRESOLVED = "kt.unresolved"
    const val KT_UNUSED_IMPORT = "kt.unusedImport"
    const val KT_UNUSED_PARAMETER = "kt.unusedParameter"
    const val KT_VAL_REASSIGN = "kt.valReassign"
    const val KT_LATEINIT = "kt.lateinit"
    const val KT_MUST_BE_INITIALIZED = "kt.mustBeInitialized"
    const val KT_NO_TYPE_NO_INITIALIZER = "kt.noTypeNoInitializer"
    const val KT_REDUNDANT_NOT_NULL = "kt.redundantNotNull"
    const val KT_REDUNDANT_SAFE_CALL = "kt.redundantSafeCall"
    const val XML_STRAY_CLOSE = "xml.strayClose"
    const val XML_EXPECTED_NAME = "xml.expectedName"
    const val XML_MALFORMED_TAG = "xml.malformedTag"
    const val XML_UNCLOSED_TAG = "xml.unclosedTag"
    const val XML_UNTERMINATED_VALUE = "xml.unterminatedValue"
    const val XML_UNQUOTED_VALUE = "xml.unquotedValue"
    const val ANDROID_MISSING_NAMESPACE = "android.missingNamespace"
    const val ANDROID_HARDCODED_TEXT = "android.hardcodedText"
    const val ANDROID_MISSING_SIZE = "android.missingSize"
}
