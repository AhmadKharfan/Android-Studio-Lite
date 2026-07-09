package com.example.androidstudiolite.feature.editor.engine
object ApiCompletionCatalog {
  fun packagesUnder(prefix: String): List<CompletionItem> {
    val normalized = prefix.trimEnd('.')
    val children = PACKAGE_TREE[normalized] ?: return emptyList()
    return children.map { (name, kind) ->
      CompletionItem(
        label = name,
        insertText = name,
        kind = if (kind == ChildKind.Package) CompletionKind.Class else kind.toCompletionKind(),
        typeText = if (kind == ChildKind.Package) "package" else kind.name.lowercase(),
      )
    }
  }
  fun membersOf(qualifier: String): List<CompletionItem> {
    val normalized = qualifier.trimEnd('.')
    val members = MEMBER_INDEX[normalized] ?: return emptyList()
    return members.map { (name, kind, detail) ->
      CompletionItem(
        label = name,
        insertText = name,
        kind = kind,
        detail = detail,
        typeText = kind.name.lowercase(),
      )
    }
  }
  fun importRoots(): List<CompletionItem> = ROOT_IMPORTS
  fun packagePathsMatching(prefix: String): List<CompletionItem> {
    if (prefix.isEmpty()) return emptyList()
    val lower = prefix.lowercase()
    return ALL_PACKAGE_PATHS
      .asSequence()
      .filter { it.lowercase().startsWith(lower) }
      .map {
        CompletionItem(
          label = it,
          insertText = it,
          kind = CompletionKind.Class,
          typeText = "package",
        )
      }
      .take(40)
      .toList()
  }
  fun topLevelSymbols(composeBias: Boolean): List<CompletionItem> {
    val out = ArrayList<CompletionItem>(KOTLIN_STDLIB.size + ANDROID_TOP_LEVEL.size)
    out.addAll(KOTLIN_STDLIB)
    out.addAll(ANDROID_TOP_LEVEL)
    if (composeBias) out.addAll(COMPOSE_TOP_LEVEL)
    return out
  }
    fun kotlinTypeNames(prefix: String = ""): List<CompletionItem> {
        return KOTLIN_TYPES
            .asSequence()
            .filter { prefix.isEmpty() || it.startsWith(prefix, ignoreCase = true) }
            .map { name -> item(name, CompletionKind.Class, "type") }
            .toList()
    }
  private enum class ChildKind { Package, Class, Function, Property }
  private fun ChildKind.toCompletionKind(): CompletionKind = when (this) {
    ChildKind.Package -> CompletionKind.Class
    ChildKind.Class -> CompletionKind.Class
    ChildKind.Function -> CompletionKind.Function
    ChildKind.Property -> CompletionKind.Property
  }
  private fun item(label: String, kind: CompletionKind, detail: String? = null, insert: String = label) =
    CompletionItem(label, insert, kind, detail, kind.name.lowercase())
  private val ROOT_IMPORTS = listOf(
    item("android", CompletionKind.Class, "package"),
    item("androidx", CompletionKind.Class, "package"),
    item("kotlin", CompletionKind.Class, "package"),
    item("kotlinx", CompletionKind.Class, "package"),
    item("java", CompletionKind.Class, "package"),
    item("javax", CompletionKind.Class, "package"),
    item("com", CompletionKind.Class, "package"),
    item("org", CompletionKind.Class, "package"),
  )
  private val PACKAGE_TREE: Map<String, List<Pair<String, ChildKind>>> = mapOf(
    "" to listOf(
      "android" to ChildKind.Package,
      "androidx" to ChildKind.Package,
      "kotlin" to ChildKind.Package,
      "kotlinx" to ChildKind.Package,
      "java" to ChildKind.Package,
      "javax" to ChildKind.Package,
      "com" to ChildKind.Package,
      "org" to ChildKind.Package,
    ),
    "android" to listOf(
      "app" to ChildKind.Package,
      "content" to ChildKind.Package,
      "graphics" to ChildKind.Package,
      "os" to ChildKind.Package,
      "util" to ChildKind.Package,
      "view" to ChildKind.Package,
      "widget" to ChildKind.Package,
      "net" to ChildKind.Package,
      "provider" to ChildKind.Package,
      "text" to ChildKind.Package,
      "media" to ChildKind.Package,
      "database" to ChildKind.Package,
    ),
    "android.app" to listOf(
      "Activity" to ChildKind.Class,
      "Application" to ChildKind.Class,
      "AlertDialog" to ChildKind.Class,
      "Service" to ChildKind.Class,
    ),
    "android.content" to listOf(
      "Context" to ChildKind.Class,
      "Intent" to ChildKind.Class,
      "SharedPreferences" to ChildKind.Class,
      "pm" to ChildKind.Package,
      "res" to ChildKind.Package,
    ),
    "android.os" to listOf(
      "Bundle" to ChildKind.Class,
      "Handler" to ChildKind.Class,
      "Looper" to ChildKind.Class,
      "Build" to ChildKind.Class,
    ),
    "android.util" to listOf(
      "Log" to ChildKind.Class,
      "SparseArray" to ChildKind.Class,
    ),
    "android.view" to listOf(
      "View" to ChildKind.Class,
      "ViewGroup" to ChildKind.Class,
    ),
    "androidx" to listOf(
      "activity" to ChildKind.Package,
      "appcompat" to ChildKind.Package,
      "core" to ChildKind.Package,
      "lifecycle" to ChildKind.Package,
      "compose" to ChildKind.Package,
      "navigation" to ChildKind.Package,
      "room" to ChildKind.Package,
      "work" to ChildKind.Package,
    ),
    "androidx.activity" to listOf(
      "ComponentActivity" to ChildKind.Class,
      "compose" to ChildKind.Package,
    ),
    "androidx.activity.compose" to listOf(
      "setContent" to ChildKind.Function,
    ),
    "androidx.core" to listOf(
      "content" to ChildKind.Package,
    ),
    "androidx.lifecycle" to listOf(
      "ViewModel" to ChildKind.Class,
      "viewmodel" to ChildKind.Package,
    ),
    "androidx.compose" to listOf(
      "foundation" to ChildKind.Package,
      "material" to ChildKind.Package,
      "material3" to ChildKind.Package,
      "runtime" to ChildKind.Package,
      "ui" to ChildKind.Package,
      "animation" to ChildKind.Package,
    ),
    "androidx.compose.foundation" to listOf(
      "layout" to ChildKind.Package,
      "text" to ChildKind.Package,
      "Image" to ChildKind.Function,
      "background" to ChildKind.Function,
      "clickable" to ChildKind.Function,
      "border" to ChildKind.Function,
    ),
    "androidx.compose.foundation.layout" to listOf(
      "Column" to ChildKind.Function,
      "Row" to ChildKind.Function,
      "Box" to ChildKind.Function,
      "Spacer" to ChildKind.Function,
      "fillMaxSize" to ChildKind.Function,
      "fillMaxWidth" to ChildKind.Function,
      "fillMaxHeight" to ChildKind.Function,
      "padding" to ChildKind.Function,
      "size" to ChildKind.Function,
      "width" to ChildKind.Function,
      "height" to ChildKind.Function,
      "weight" to ChildKind.Function,
      "Arrangement" to ChildKind.Class,
      "Alignment" to ChildKind.Class,
    ),
    "androidx.compose.material3" to listOf(
      "Text" to ChildKind.Function,
      "Button" to ChildKind.Function,
      "Icon" to ChildKind.Function,
      "IconButton" to ChildKind.Function,
      "Scaffold" to ChildKind.Function,
      "TopAppBar" to ChildKind.Function,
      "Card" to ChildKind.Function,
      "Surface" to ChildKind.Function,
      "Switch" to ChildKind.Function,
      "Checkbox" to ChildKind.Function,
      "TextField" to ChildKind.Function,
      "FloatingActionButton" to ChildKind.Function,
      "MaterialTheme" to ChildKind.Class,
      "AlertDialog" to ChildKind.Function,
      "Divider" to ChildKind.Function,
      "LinearProgressIndicator" to ChildKind.Function,
      "CircularProgressIndicator" to ChildKind.Function,
    ),
    "androidx.compose.runtime" to listOf(
      "Composable" to ChildKind.Class,
      "getValue" to ChildKind.Function,
      "setValue" to ChildKind.Function,
      "mutableStateOf" to ChildKind.Function,
      "remember" to ChildKind.Function,
      "rememberSaveable" to ChildKind.Function,
      "LaunchedEffect" to ChildKind.Function,
      "DisposableEffect" to ChildKind.Function,
      "derivedStateOf" to ChildKind.Function,
      "produceState" to ChildKind.Function,
      "SideEffect" to ChildKind.Function,
    ),
    "androidx.compose.ui" to listOf(
      "Modifier" to ChildKind.Class,
      "Alignment" to ChildKind.Class,
      "Color" to ChildKind.Class,
      "graphics" to ChildKind.Package,
      "unit" to ChildKind.Package,
    ),
    "androidx.compose.ui.graphics" to listOf(
      "Color" to ChildKind.Class,
      "Brush" to ChildKind.Class,
      "vector" to ChildKind.Package,
    ),
    "androidx.compose.ui.unit" to listOf(
      "dp" to ChildKind.Function,
      "sp" to ChildKind.Function,
      "Dp" to ChildKind.Class,
      "TextUnit" to ChildKind.Class,
    ),
    "kotlin" to listOf(
      "collections" to ChildKind.Package,
      "text" to ChildKind.Package,
      "io" to ChildKind.Package,
    ),
    "kotlinx" to listOf(
      "coroutines" to ChildKind.Package,
    ),
    "kotlinx.coroutines" to listOf(
      "delay" to ChildKind.Function,
      "launch" to ChildKind.Function,
      "async" to ChildKind.Function,
      "withContext" to ChildKind.Function,
      "flow" to ChildKind.Package,
      "Dispatchers" to ChildKind.Class,
      "CoroutineScope" to ChildKind.Class,
    ),
    "java" to listOf(
      "util" to ChildKind.Package,
      "io" to ChildKind.Package,
      "lang" to ChildKind.Package,
    ),
    "java.util" to listOf(
      "List" to ChildKind.Class,
      "Map" to ChildKind.Class,
      "Set" to ChildKind.Class,
      "ArrayList" to ChildKind.Class,
      "HashMap" to ChildKind.Class,
    ),
  )
  private val MEMBER_INDEX: Map<String, List<Triple<String, CompletionKind, String?>>> = mapOf(
    "Log" to listOf(
      Triple("d", CompletionKind.Method, "Log.d(tag, msg)"),
      Triple("e", CompletionKind.Method, "Log.e(tag, msg)"),
      Triple("i", CompletionKind.Method, "Log.i(tag, msg)"),
      Triple("v", CompletionKind.Method, "Log.v(tag, msg)"),
      Triple("w", CompletionKind.Method, "Log.w(tag, msg)"),
    ),
    "Modifier" to listOf(
      Triple("fillMaxSize", CompletionKind.Method, "()"),
      Triple("fillMaxWidth", CompletionKind.Method, "()"),
      Triple("fillMaxHeight", CompletionKind.Method, "()"),
      Triple("padding", CompletionKind.Method, "(…)"),
      Triple("background", CompletionKind.Method, "(color)"),
      Triple("clickable", CompletionKind.Method, "{ onClick }"),
      Triple("size", CompletionKind.Method, "(dp)"),
      Triple("width", CompletionKind.Method, "(dp)"),
      Triple("height", CompletionKind.Method, "(dp)"),
      Triple("weight", CompletionKind.Method, "(1f)"),
      Triple("align", CompletionKind.Method, "(Alignment)"),
      Triple("offset", CompletionKind.Method, "(x, y)"),
      Triple("border", CompletionKind.Method, "(width, color)"),
      Triple("clip", CompletionKind.Method, "(shape)"),
      Triple("alpha", CompletionKind.Method, "(0f..1f)"),
      Triple("verticalScroll", CompletionKind.Method, "(state)"),
      Triple("horizontalScroll", CompletionKind.Method, "(state)"),
    ),
    "Bundle" to listOf(
      Triple("getString", CompletionKind.Method, "(key)"),
      Triple("getInt", CompletionKind.Method, "(key)"),
      Triple("putString", CompletionKind.Method, "(key, value)"),
      Triple("putInt", CompletionKind.Method, "(key, value)"),
    ),
    "Context" to listOf(
      Triple("getString", CompletionKind.Method, "(resId)"),
      Triple("getSharedPreferences", CompletionKind.Method, "(name, mode)"),
    ),
    "String" to listOf(
      Triple("isEmpty", CompletionKind.Method, "()"),
      Triple("isNotEmpty", CompletionKind.Method, "()"),
      Triple("isBlank", CompletionKind.Method, "()"),
      Triple("trim", CompletionKind.Method, "()"),
      Triple("lowercase", CompletionKind.Method, "()"),
      Triple("uppercase", CompletionKind.Method, "()"),
      Triple("substring", CompletionKind.Method, "(…)"),
      Triple("contains", CompletionKind.Method, "(other)"),
      Triple("split", CompletionKind.Method, "(delimiter)"),
    ),
    "List" to listOf(
      Triple("size", CompletionKind.Property, "Int"),
      Triple("isEmpty", CompletionKind.Method, "()"),
      Triple("isNotEmpty", CompletionKind.Method, "()"),
      Triple("first", CompletionKind.Method, "()"),
      Triple("last", CompletionKind.Method, "()"),
      Triple("filter", CompletionKind.Method, "{ }"),
      Triple("map", CompletionKind.Method, "{ }"),
      Triple("forEach", CompletionKind.Method, "{ }"),
    ),
  )
  private val KOTLIN_TYPES = listOf(
    "String", "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char",
    "Unit", "Any", "Nothing", "Number", "Comparable", "Iterable", "Collection",
    "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap", "Array",
    "Pair", "Triple", "Result", "Lazy", "Sequence",
    "Modifier", "Color", "Dp", "TextUnit", "Alignment", "Arrangement",
    "ImageBitmap", "Painter", "ColorFilter", "ContentScale", "FontWeight", "TextStyle",
    "Bundle", "Context", "Intent", "ViewModel", "ComponentActivity",
  )
  private val KOTLIN_STDLIB = listOf(
    item("println", CompletionKind.Function, "print line"),
    item("print", CompletionKind.Function, "print"),
    item("listOf", CompletionKind.Function, "immutable list"),
    item("mutableListOf", CompletionKind.Function, "mutable list"),
    item("mapOf", CompletionKind.Function, "immutable map"),
    item("mutableMapOf", CompletionKind.Function, "mutable map"),
    item("setOf", CompletionKind.Function, "immutable set"),
    item("arrayOf", CompletionKind.Function, "array"),
    item("lazy", CompletionKind.Function, "lazy init"),
    item("require", CompletionKind.Function, "contract"),
    item("check", CompletionKind.Function, "contract"),
    item("error", CompletionKind.Function, "throw"),
    item("TODO", CompletionKind.Function, "stub"),
    item("enumValues", CompletionKind.Function, "enum"),
    item("run", CompletionKind.Function, "scope"),
    item("with", CompletionKind.Function, "scope"),
    item("apply", CompletionKind.Function, "scope"),
    item("also", CompletionKind.Function, "scope"),
    item("let", CompletionKind.Function, "scope"),
    item("takeIf", CompletionKind.Function, "filter"),
    item("takeUnless", CompletionKind.Function, "filter"),
  )
  private val ANDROID_TOP_LEVEL = listOf(
    item("Log", CompletionKind.Class, "android.util.Log"),
    item("Bundle", CompletionKind.Class, "android.os.Bundle"),
    item("Intent", CompletionKind.Class, "android.content.Intent"),
    item("Context", CompletionKind.Class, "android.content.Context"),
    item("Activity", CompletionKind.Class, "android.app.Activity"),
    item("ComponentActivity", CompletionKind.Class, "androidx.activity.ComponentActivity"),
    item("ViewModel", CompletionKind.Class, "androidx.lifecycle.ViewModel"),
  )
  private val COMPOSE_TOP_LEVEL = listOf(
    item("Text", CompletionKind.Function, "@Composable"),
    item("Button", CompletionKind.Function, "@Composable"),
    item("Image", CompletionKind.Function, "@Composable"),
    item("Icon", CompletionKind.Function, "@Composable"),
    item("Column", CompletionKind.Function, "@Composable"),
    item("Row", CompletionKind.Function, "@Composable"),
    item("Box", CompletionKind.Function, "@Composable"),
    item("Spacer", CompletionKind.Function, "@Composable"),
    item("Scaffold", CompletionKind.Function, "@Composable"),
    item("Surface", CompletionKind.Function, "@Composable"),
    item("Card", CompletionKind.Function, "@Composable"),
    item("TopAppBar", CompletionKind.Function, "@Composable"),
    item("TextField", CompletionKind.Function, "@Composable"),
    item("IconButton", CompletionKind.Function, "@Composable"),
    item("FloatingActionButton", CompletionKind.Function, "@Composable"),
    item("Switch", CompletionKind.Function, "@Composable"),
    item("Checkbox", CompletionKind.Function, "@Composable"),
    item("Divider", CompletionKind.Function, "@Composable"),
    item("LinearProgressIndicator", CompletionKind.Function, "@Composable"),
    item("CircularProgressIndicator", CompletionKind.Function, "@Composable"),
    item("AlertDialog", CompletionKind.Function, "@Composable"),
    item("Modifier", CompletionKind.Class, "layout modifier"),
    item("Color", CompletionKind.Class, "ui.graphics.Color"),
    item("dp", CompletionKind.Function, "Dp"),
    item("sp", CompletionKind.Function, "TextUnit"),
    item("remember", CompletionKind.Function, "runtime"),
    item("mutableStateOf", CompletionKind.Function, "runtime"),
    item("LaunchedEffect", CompletionKind.Function, "runtime"),
    item("DisposableEffect", CompletionKind.Function, "runtime"),
    item("derivedStateOf", CompletionKind.Function, "runtime"),
    item("setContent", CompletionKind.Function, "activity.compose"),
    item("MaterialTheme", CompletionKind.Class, "material3"),
    item("Composable", CompletionKind.Class, "annotation"),
    item("Preview", CompletionKind.Class, "annotation"),
    item("Alignment", CompletionKind.Class, "ui"),
    item("Arrangement", CompletionKind.Class, "layout"),
  )
  private val ALL_PACKAGE_PATHS: List<String> by lazy {
    val paths = LinkedHashSet<String>()
    fun walk(prefix: String) {
      val children = PACKAGE_TREE[prefix] ?: return
      for ((name, kind) in children) {
        if (kind != ChildKind.Package) continue
        val full = if (prefix.isEmpty()) name else "$prefix.$name"
        paths.add(full)
        walk(full)
      }
    }
    walk("")
    paths.toList()
  }
}
