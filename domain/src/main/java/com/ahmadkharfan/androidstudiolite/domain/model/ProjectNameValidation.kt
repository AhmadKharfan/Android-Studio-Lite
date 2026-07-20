package com.ahmadkharfan.androidstudiolite.domain.model

sealed interface ProjectNameValidation {
    data object Valid : ProjectNameValidation
    data class Invalid(val reason: String) : ProjectNameValidation
}

fun validateProjectName(name: String): ProjectNameValidation = when {
    name.isBlank() -> ProjectNameValidation.Invalid("Project name can't be empty")
    !name.first().isLetter() -> ProjectNameValidation.Invalid("Name must start with a letter")
    name.any { it.isWhitespace() } -> ProjectNameValidation.Invalid("Name can't contain spaces")
    else -> ProjectNameValidation.Valid
}

fun validatePackageName(packageName: String): ProjectNameValidation {
    val segments = packageName.split('.')
    return when {
        packageName.isBlank() -> ProjectNameValidation.Invalid("Package name can't be empty")
        packageName.any { it.isWhitespace() } ->
            ProjectNameValidation.Invalid("Package name can't contain spaces")
        packageName != packageName.lowercase() ->
            ProjectNameValidation.Invalid("Package name must be lowercase")
        segments.size < 2 ->
            ProjectNameValidation.Invalid("Package name needs at least two parts, e.g. com.example")
        segments.any { it.isEmpty() } ->
            ProjectNameValidation.Invalid("Package name can't have an empty part")
        segments.any { !it.matches(PACKAGE_SEGMENT) } ->
            ProjectNameValidation.Invalid("Each part must start with a letter and use only letters, digits or _")
        segments.firstOrNull { it in JAVA_KEYWORDS } != null ->
            ProjectNameValidation.Invalid(
                "\"${segments.first { it in JAVA_KEYWORDS }}\" is a reserved keyword",
            )
        else -> ProjectNameValidation.Valid
    }
}

private val PACKAGE_SEGMENT = Regex("[a-z][a-z0-9_]*")

private val JAVA_KEYWORDS = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
    "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
    "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
    "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
    "volatile", "while", "true", "false", "null", "_",
)
