package com.ahmadkharfan.androidstudiolite.feature.editor.engine
object CallSignatureCatalog {
    data class Param(val name: String, val type: String? = null, val required: Boolean = false)
    private val SIGNATURES: Map<String, List<Param>> = mapOf(
        "Text" to listOf(
            Param("text", "String", required = true),
            Param("modifier", "Modifier"),
            Param("color", "Color"),
            Param("fontSize", "TextUnit"),
            Param("fontStyle", "FontStyle"),
            Param("fontWeight", "FontWeight"),
            Param("fontFamily", "FontFamily"),
            Param("letterSpacing", "TextUnit"),
            Param("textDecoration", "TextDecoration"),
            Param("textAlign", "TextAlign"),
            Param("lineHeight", "TextUnit"),
            Param("overflow", "TextOverflow"),
            Param("softWrap", "Boolean"),
            Param("maxLines", "Int"),
            Param("minLines", "Int"),
            Param("style", "TextStyle"),
        ),
        "Button" to listOf(
            Param("onClick", "Function", required = true),
            Param("modifier", "Modifier"),
            Param("enabled", "Boolean"),
            Param("shape", "Shape"),
            Param("colors", "ButtonColors"),
            Param("elevation", "ButtonElevation"),
            Param("border", "BorderStroke"),
            Param("contentPadding", "PaddingValues"),
            Param("content", "Function"),
        ),
        "TextField" to listOf(
            Param("value", "String"),
            Param("onValueChange", "Function"),
            Param("modifier", "Modifier"),
            Param("enabled", "Boolean"),
            Param("readOnly", "Boolean"),
            Param("label", "Function"),
            Param("placeholder", "Function"),
            Param("leadingIcon", "Function"),
            Param("trailingIcon", "Function"),
            Param("isError", "Boolean"),
            Param("singleLine", "Boolean"),
            Param("maxLines", "Int"),
            Param("shape", "Shape"),
            Param("colors", "TextFieldColors"),
        ),
        "Column" to listOf(
            Param("modifier", "Modifier"),
            Param("verticalArrangement", "Arrangement.Vertical"),
            Param("horizontalAlignment", "Alignment.Horizontal"),
            Param("content", "Function"),
        ),
        "Row" to listOf(
            Param("modifier", "Modifier"),
            Param("horizontalArrangement", "Arrangement.Horizontal"),
            Param("verticalAlignment", "Alignment.Vertical"),
            Param("content", "Function"),
        ),
        "Box" to listOf(
            Param("modifier", "Modifier"),
            Param("contentAlignment", "Alignment"),
            Param("propagateMinConstraints", "Boolean"),
            Param("content", "Function"),
        ),
        "Scaffold" to listOf(
            Param("modifier", "Modifier"),
            Param("topBar", "Function"),
            Param("bottomBar", "Function"),
            Param("snackbarHost", "Function"),
            Param("floatingActionButton", "Function"),
            Param("floatingActionButtonPosition", "FabPosition"),
            Param("containerColor", "Color"),
            Param("contentColor", "Color"),
            Param("content", "Function"),
        ),
        "Surface" to listOf(
            Param("modifier", "Modifier"),
            Param("shape", "Shape"),
            Param("color", "Color"),
            Param("contentColor", "Color"),
            Param("tonalElevation", "Dp"),
            Param("shadowElevation", "Dp"),
            Param("border", "BorderStroke"),
            Param("content", "Function"),
        ),
        "Card" to listOf(
            Param("modifier", "Modifier"),
            Param("shape", "Shape"),
            Param("colors", "CardColors"),
            Param("elevation", "CardElevation"),
            Param("border", "BorderStroke"),
            Param("content", "Function"),
        ),
        "Image" to listOf(
            Param("bitmap", "ImageBitmap", required = true),
            Param("contentDescription", "String", required = true),
            Param("modifier", "Modifier"),
            Param("alignment", "Alignment"),
            Param("contentScale", "ContentScale"),
            Param("alpha", "Float"),
            Param("colorFilter", "ColorFilter"),
        ),
        "Icon" to listOf(
            Param("imageVector", "ImageVector"),
            Param("contentDescription", "String"),
            Param("modifier", "Modifier"),
            Param("tint", "Color"),
        ),
        "TopAppBar" to listOf(
            Param("title", "Function"),
            Param("modifier", "Modifier"),
            Param("navigationIcon", "Function"),
            Param("actions", "Function"),
            Param("colors", "TopAppBarColors"),
        ),
        "Switch" to listOf(
            Param("checked", "Boolean"),
            Param("onCheckedChange", "Function"),
            Param("modifier", "Modifier"),
            Param("enabled", "Boolean"),
            Param("colors", "SwitchColors"),
        ),
        "Checkbox" to listOf(
            Param("checked", "Boolean"),
            Param("onCheckedChange", "Function"),
            Param("modifier", "Modifier"),
            Param("enabled", "Boolean"),
            Param("colors", "CheckboxColors"),
        ),
        "FloatingActionButton" to listOf(
            Param("onClick", "Function"),
            Param("modifier", "Modifier"),
            Param("shape", "Shape"),
            Param("containerColor", "Color"),
            Param("contentColor", "Color"),
            Param("elevation", "FloatingActionButtonElevation"),
            Param("content", "Function"),
        ),
        "padding" to listOf(
            Param("start", "Dp"),
            Param("top", "Dp"),
            Param("end", "Dp"),
            Param("bottom", "Dp"),
            Param("horizontal", "Dp"),
            Param("vertical", "Dp"),
            Param("all", "Dp"),
        ),
        "background" to listOf(
            Param("color", "Color"),
            Param("shape", "Shape"),
            Param("alpha", "Float"),
        ),
        "clickable" to listOf(
            Param("enabled", "Boolean"),
            Param("onClickLabel", "String"),
            Param("role", "Role"),
            Param("onClick", "Function"),
        ),
        "size" to listOf(
            Param("size", "Dp"),
            Param("width", "Dp"),
            Param("height", "Dp"),
        ),
        "border" to listOf(
            Param("width", "Dp"),
            Param("color", "Color"),
            Param("shape", "Shape"),
        ),
        "fillMaxSize" to listOf(Param("fraction", "Float")),
        "fillMaxWidth" to listOf(Param("fraction", "Float")),
        "fillMaxHeight" to listOf(Param("fraction", "Float")),
        "weight" to listOf(Param("weight", "Float"), Param("fill", "Boolean")),
        "align" to listOf(Param("alignment", "Alignment")),
        "offset" to listOf(Param("x", "Dp"), Param("y", "Dp")),
        "remember" to listOf(Param("calculation", "Function")),
        "LaunchedEffect" to listOf(Param("key1", "Any"), Param("block", "Function")),
        "mutableStateOf" to listOf(Param("value", "Any")),
        "Log.d" to listOf(Param("tag", "String"), Param("msg", "String")),
        "Log.e" to listOf(Param("tag", "String"), Param("msg", "String")),
        "Log.i" to listOf(Param("tag", "String"), Param("msg", "String")),
        "Log.w" to listOf(Param("tag", "String"), Param("msg", "String")),
        "Log.v" to listOf(Param("tag", "String"), Param("msg", "String")),
        "d" to listOf(Param("tag", "String"), Param("msg", "String")),
        "e" to listOf(Param("tag", "String"), Param("msg", "String")),
        "i" to listOf(Param("tag", "String"), Param("msg", "String")),
        "w" to listOf(Param("tag", "String"), Param("msg", "String")),
        "v" to listOf(Param("tag", "String"), Param("msg", "String")),
        "setContent" to listOf(Param("content", "Function")),
        "launch" to listOf(Param("context", "CoroutineContext"), Param("start", "CoroutineStart"), Param("block", "Function")),
        "delay" to listOf(Param("timeMillis", "Long")),
        "withContext" to listOf(Param("context", "CoroutineContext"), Param("block", "Function")),
        "User" to listOf(Param("name", "String"), Param("age", "Int")),
        "makeUser" to listOf(Param("name", "String"), Param("age", "Int")),
        "box" to listOf(Param("w", "Int")),
    )
    fun parametersFor(calleeName: String): List<Param> {
        SIGNATURES[calleeName]?.let { return it }
        val simple = calleeName.substringAfterLast('.')
        return SIGNATURES[simple].orEmpty()
    }
    fun expectedTypeAt(calleeName: String, activeIndex: Int, namedArg: String?): String? {
        val params = parametersFor(calleeName)
        if (params.isEmpty()) return null
        if (namedArg != null) {
            return params.firstOrNull { it.name == namedArg }?.type
        }
        return params.getOrNull(activeIndex)?.type
    }
    fun namedArgItems(
        calleeName: String,
        prefix: String,
        supplied: Set<String>,
        editingLabel: Boolean,
    ): List<CompletionItem> = namedArgItemsFromParams(parametersFor(calleeName), prefix, supplied, editingLabel)
    fun namedArgItemsFromParams(
        params: List<Param>,
        prefix: String,
        supplied: Set<String>,
        editingLabel: Boolean,
    ): List<CompletionItem> {
        return params
            .asSequence()
            .filter { it.name !in supplied }
            .filter { prefix.isEmpty() || it.name.startsWith(prefix, ignoreCase = true) }
            .map { param ->
                val label = "${param.name} ="
                val insert = if (editingLabel) param.name else "${param.name} = "
                CompletionItem(
                    label = label,
                    insertText = insert,
                    kind = CompletionKind.Parameter,
                    detail = param.type,
                    typeText = "parameter",
                )
            }
            .toList()
    }
    fun expectedTypeExtras(expectedType: String?): List<CompletionItem> {
        if (expectedType == null) return emptyList()
        val simple = expectedType.substringBefore('<').trim().substringAfterLast('.')
        return when {
            simple.equals("Boolean", ignoreCase = true) -> listOf(
                CompletionItem("true", "true", CompletionKind.Keyword, typeText = "Boolean"),
                CompletionItem("false", "false", CompletionKind.Keyword, typeText = "Boolean"),
            )
            simple.equals("Color", ignoreCase = true) -> COLOR_CONSTANTS
            simple.contains("Dp", ignoreCase = true) -> typeConstants("Dp")
            simple.contains("Alignment", ignoreCase = true) -> typeConstants("Alignment")
            simple.contains("Arrangement", ignoreCase = true) -> typeConstants("Arrangement")
            simple.contains("TextAlign", ignoreCase = true) -> typeConstants("TextAlign")
            simple.contains("FontWeight", ignoreCase = true) -> typeConstants("FontWeight")
            simple.equals("Modifier", ignoreCase = true) -> ApiCompletionCatalog.membersOf("Modifier")
            else -> emptyList()
        }
    }
    fun typeConstants(type: String): List<CompletionItem> = when (type) {
        "Color" -> COLOR_CONSTANTS
        "Alignment" -> ALIGNMENT_CONSTANTS
        "Arrangement", "Arrangement.Horizontal", "Arrangement.Vertical" -> ARRANGEMENT_CONSTANTS
        "Dp" -> listOf(
            CompletionItem("0.dp", "0.dp", CompletionKind.Property, "Dp", "unit"),
            CompletionItem("8.dp", "8.dp", CompletionKind.Property, "Dp", "unit"),
            CompletionItem("16.dp", "16.dp", CompletionKind.Property, "Dp", "unit"),
        )
        "TextUnit" -> listOf(
            CompletionItem("14.sp", "14.sp", CompletionKind.Property, "TextUnit", "unit"),
            CompletionItem("16.sp", "16.sp", CompletionKind.Property, "TextUnit", "unit"),
        )
        "TextAlign" -> listOf(
            CompletionItem("TextAlign.Center", "TextAlign.Center", CompletionKind.Property, "TextAlign"),
            CompletionItem("TextAlign.Start", "TextAlign.Start", CompletionKind.Property, "TextAlign"),
            CompletionItem("TextAlign.End", "TextAlign.End", CompletionKind.Property, "TextAlign"),
        )
        "FontWeight" -> listOf(
            CompletionItem("FontWeight.Bold", "FontWeight.Bold", CompletionKind.Property, "FontWeight"),
            CompletionItem("FontWeight.Normal", "FontWeight.Normal", CompletionKind.Property, "FontWeight"),
        )
        else -> emptyList()
    }
    fun matchesExpectedType(item: CompletionItem, expectedType: String): Boolean {
        val type = expectedType.substringBefore('<').trim()
        return when {
            item.typeText?.equals(type, ignoreCase = true) == true -> true
            item.detail?.contains(type, ignoreCase = true) == true -> true
            item.label.equals(type, ignoreCase = true) -> true
            type == "Modifier" && item.label == "Modifier" -> true
            type == "String" && item.kind == CompletionKind.Variable -> false
            type == "Boolean" && item.label in setOf("true", "false") -> true
            type == "Color" && item.detail?.contains("Color") == true -> true
            type == "Function" -> item.kind == CompletionKind.Function || item.kind == CompletionKind.Snippet
            else -> false
        }
    }
    private val COLOR_CONSTANTS = listOf(
        CompletionItem("Color.Black", "Color.Black", CompletionKind.Property, "Color", "const"),
        CompletionItem("Color.White", "Color.White", CompletionKind.Property, "Color", "const"),
        CompletionItem("Color.Red", "Color.Red", CompletionKind.Property, "Color", "const"),
        CompletionItem("Color.Green", "Color.Green", CompletionKind.Property, "Color", "const"),
        CompletionItem("Color.Blue", "Color.Blue", CompletionKind.Property, "Color", "const"),
        CompletionItem("Color.Transparent", "Color.Transparent", CompletionKind.Property, "Color", "const"),
    )
    private val ALIGNMENT_CONSTANTS = listOf(
        CompletionItem("Alignment.Center", "Alignment.Center", CompletionKind.Property, "Alignment"),
        CompletionItem("Alignment.TopStart", "Alignment.TopStart", CompletionKind.Property, "Alignment"),
        CompletionItem("Alignment.TopCenter", "Alignment.TopCenter", CompletionKind.Property, "Alignment"),
        CompletionItem("Alignment.CenterStart", "Alignment.CenterStart", CompletionKind.Property, "Alignment"),
        CompletionItem("Alignment.CenterEnd", "Alignment.CenterEnd", CompletionKind.Property, "Alignment"),
        CompletionItem("Alignment.BottomCenter", "Alignment.BottomCenter", CompletionKind.Property, "Alignment"),
    )
    private val ARRANGEMENT_CONSTANTS = listOf(
        CompletionItem("Arrangement.Center", "Arrangement.Center", CompletionKind.Property, "Arrangement"),
        CompletionItem("Arrangement.spacedBy(8.dp)", "Arrangement.spacedBy(8.dp)", CompletionKind.Function, "Arrangement"),
        CompletionItem("Arrangement.Top", "Arrangement.Top", CompletionKind.Property, "Arrangement"),
        CompletionItem("Arrangement.Bottom", "Arrangement.Bottom", CompletionKind.Property, "Arrangement"),
    )
}
