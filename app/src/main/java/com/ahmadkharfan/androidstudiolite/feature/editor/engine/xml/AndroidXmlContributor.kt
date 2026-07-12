package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionKind

object AndroidXmlContributor : XmlCompletionContributor {

    override fun contribute(position: XmlCompletionPosition): List<CompletionItem> {
        val manifest = isManifest(position)
        return when (position.kind) {
            XmlCompletionKind.TAG_NAME -> if (manifest) MANIFEST_TAGS else LAYOUT_TAGS
            XmlCompletionKind.ATTRIBUTE_NAME ->
                (if (manifest) MANIFEST_ATTRS else LAYOUT_ATTRS).filter { it.label !in position.existingAttributes }
            XmlCompletionKind.ATTRIBUTE_VALUE -> valuesFor(position.attributeName)
            XmlCompletionKind.TEXT, XmlCompletionKind.UNKNOWN -> emptyList()
        }
    }

    private fun isManifest(position: XmlCompletionPosition): Boolean =
        position.filePath.substringAfterLast('/').equals("AndroidManifest.xml", ignoreCase = true) ||
            position.parentTag in MANIFEST_CONTAINERS || position.tag in MANIFEST_CONTAINERS

    private fun valuesFor(attrName: String?): List<CompletionItem> {
        val local = attrName?.substringAfterLast(':') ?: return emptyList()
        return VALUE_CATALOG[local].orEmpty()
    }

    private fun tag(name: String) = CompletionItem(name, name, CompletionKind.Class, typeText = "tag")
    private fun attr(name: String) = CompletionItem(name, "$name=\"\$0\"", CompletionKind.Property, typeText = "attribute")
    private fun value(name: String) = CompletionItem(name, name, CompletionKind.Variable, typeText = "value")

    private val MANIFEST_CONTAINERS = setOf("manifest", "application", "activity", "service", "receiver", "provider", "intent-filter")

    private val LAYOUT_TAGS = listOf(
        "LinearLayout", "RelativeLayout", "FrameLayout", "ConstraintLayout",
        "androidx.constraintlayout.widget.ConstraintLayout", "TextView", "Button", "ImageView", "EditText",
        "ScrollView", "RecyclerView", "CardView", "androidx.cardview.widget.CardView", "Switch", "CheckBox",
        "RadioButton", "RadioGroup", "ProgressBar", "SeekBar", "Spinner", "Space", "View", "ImageButton",
        "com.google.android.material.button.MaterialButton",
        "com.google.android.material.textfield.TextInputLayout", "include", "merge", "fragment",
    ).map(::tag)

    private val LAYOUT_ATTRS = listOf(
        "android:layout_width", "android:layout_height", "android:id", "android:text", "android:textSize",
        "android:textColor", "android:textStyle", "android:background", "android:padding", "android:paddingStart",
        "android:paddingEnd", "android:paddingTop", "android:paddingBottom", "android:layout_margin",
        "android:layout_marginStart", "android:layout_marginTop", "android:orientation", "android:gravity",
        "android:layout_gravity", "android:visibility", "android:src", "android:contentDescription",
        "android:onClick", "android:hint", "android:inputType", "android:enabled", "android:clickable",
        "android:focusable", "android:maxLines", "android:ellipsize",
        "app:layout_constraintTop_toTopOf", "app:layout_constraintBottom_toBottomOf",
        "app:layout_constraintStart_toStartOf", "app:layout_constraintEnd_toEndOf",
        "tools:text", "xmlns:android", "xmlns:app", "xmlns:tools",
    ).map(::attr)

    private val MANIFEST_TAGS = listOf(
        "manifest", "application", "activity", "service", "receiver", "provider", "uses-permission",
        "uses-feature", "uses-sdk", "intent-filter", "action", "category", "data", "meta-data", "permission",
        "uses-library",
    ).map(::tag)

    private val MANIFEST_ATTRS = listOf(
        "android:name", "android:exported", "android:label", "android:icon", "android:theme",
        "android:allowBackup", "android:roundIcon", "android:permission", "android:enabled", "android:process",
        "android:launchMode", "android:screenOrientation", "android:configChanges", "android:value",
        "android:required", "xmlns:android", "xmlns:tools", "package",
    ).map(::attr)

    private val VALUE_CATALOG: Map<String, List<CompletionItem>> = mapOf(
        "layout_width" to listOf("match_parent", "wrap_content", "0dp").map(::value),
        "layout_height" to listOf("match_parent", "wrap_content", "0dp").map(::value),
        "orientation" to listOf("vertical", "horizontal").map(::value),
        "visibility" to listOf("visible", "invisible", "gone").map(::value),
        "gravity" to listOf("center", "start", "end", "top", "bottom", "center_horizontal", "center_vertical").map(::value),
        "layout_gravity" to listOf("center", "start", "end", "top", "bottom").map(::value),
        "textStyle" to listOf("normal", "bold", "italic").map(::value),
        "inputType" to listOf("text", "textPassword", "textEmailAddress", "number", "phone", "textMultiLine").map(::value),
        "ellipsize" to listOf("none", "start", "middle", "end", "marquee").map(::value),
        "exported" to listOf("true", "false").map(::value),
        "allowBackup" to listOf("true", "false").map(::value),
        "enabled" to listOf("true", "false").map(::value),
        "clickable" to listOf("true", "false").map(::value),
        "focusable" to listOf("true", "false").map(::value),
        "required" to listOf("true", "false").map(::value),
        "launchMode" to listOf("standard", "singleTop", "singleTask", "singleInstance").map(::value),
        "screenOrientation" to listOf("portrait", "landscape", "sensor", "behind").map(::value),
    )
}
