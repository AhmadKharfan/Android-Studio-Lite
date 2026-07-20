package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionKind

object AndroidXmlContributor : XmlCompletionContributor {

    override fun contribute(position: XmlCompletionPosition): List<CompletionItem> {
        val manifest = isManifestContext(position)
        return when (position.kind) {
            XmlCompletionKind.TAG_NAME ->
                if (manifest) manifestTags else layoutTags
            XmlCompletionKind.ATTRIBUTE_NAME ->
                (if (manifest) manifestAttributes else layoutAttributes)
                    .filter { it.label !in position.existingAttributes }
            XmlCompletionKind.ATTRIBUTE_VALUE ->
                enumeratedValues(position.attributeName)
            XmlCompletionKind.TEXT, XmlCompletionKind.UNKNOWN ->
                emptyList()
        }
    }

    private fun isManifestContext(position: XmlCompletionPosition): Boolean {
        val fileName = position.filePath.substringAfterLast('/')
        if (fileName.equals("AndroidManifest.xml", ignoreCase = true)) return true
        return position.tag in MANIFEST_ELEMENTS || position.parentTag in MANIFEST_ELEMENTS
    }

    private fun enumeratedValues(attributeName: String?): List<CompletionItem> {
        val localName = attributeName?.substringAfterLast(':') ?: return emptyList()
        return VALUE_SETS[localName].orEmpty().map(::valueItem)
    }


    private fun tagItem(name: String) =
        CompletionItem(name, name, CompletionKind.Class, typeText = "tag")

    private fun attributeItem(name: String) =
        CompletionItem(name, "$name=\"\$0\"", CompletionKind.Property, typeText = "attribute")

    private fun valueItem(name: String) =
        CompletionItem(name, name, CompletionKind.Variable, typeText = "value")


    private val MANIFEST_ELEMENTS = setOf(
        "manifest", "application", "activity", "service", "receiver", "provider", "intent-filter",
    )

    private val layoutTags = listOf(
        "LinearLayout", "RelativeLayout", "FrameLayout", "ConstraintLayout",
        "androidx.constraintlayout.widget.ConstraintLayout", "TextView", "Button", "ImageView",
        "EditText", "ScrollView", "RecyclerView", "CardView", "androidx.cardview.widget.CardView",
        "Switch", "CheckBox", "RadioButton", "RadioGroup", "ProgressBar", "SeekBar", "Spinner",
        "Space", "View", "ImageButton", "com.google.android.material.button.MaterialButton",
        "com.google.android.material.textfield.TextInputLayout", "include", "merge", "fragment",
    ).map(::tagItem)

    private val layoutAttributes = listOf(
        "android:layout_width", "android:layout_height", "android:id", "android:text",
        "android:textSize", "android:textColor", "android:textStyle", "android:background",
        "android:padding", "android:paddingStart", "android:paddingEnd", "android:paddingTop",
        "android:paddingBottom", "android:layout_margin", "android:layout_marginStart",
        "android:layout_marginTop", "android:orientation", "android:gravity", "android:layout_gravity",
        "android:visibility", "android:src", "android:contentDescription", "android:onClick",
        "android:hint", "android:inputType", "android:enabled", "android:clickable",
        "android:focusable", "android:maxLines", "android:ellipsize",
        "app:layout_constraintTop_toTopOf", "app:layout_constraintBottom_toBottomOf",
        "app:layout_constraintStart_toStartOf", "app:layout_constraintEnd_toEndOf",
        "tools:text", "xmlns:android", "xmlns:app", "xmlns:tools",
    ).map(::attributeItem)

    private val manifestTags = listOf(
        "manifest", "application", "activity", "service", "receiver", "provider", "uses-permission",
        "uses-feature", "uses-sdk", "intent-filter", "action", "category", "data", "meta-data",
        "permission", "uses-library",
    ).map(::tagItem)

    private val manifestAttributes = listOf(
        "android:name", "android:exported", "android:label", "android:icon", "android:theme",
        "android:allowBackup", "android:roundIcon", "android:permission", "android:enabled",
        "android:process", "android:launchMode", "android:screenOrientation", "android:configChanges",
        "android:value", "android:required", "xmlns:android", "xmlns:tools", "package",
    ).map(::attributeItem)

    private val VALUE_SETS: Map<String, List<String>> = mapOf(
        "layout_width" to listOf("match_parent", "wrap_content", "0dp"),
        "layout_height" to listOf("match_parent", "wrap_content", "0dp"),
        "orientation" to listOf("vertical", "horizontal"),
        "visibility" to listOf("visible", "invisible", "gone"),
        "gravity" to listOf("center", "start", "end", "top", "bottom", "center_horizontal", "center_vertical"),
        "layout_gravity" to listOf("center", "start", "end", "top", "bottom"),
        "textStyle" to listOf("normal", "bold", "italic"),
        "inputType" to listOf("text", "textPassword", "textEmailAddress", "number", "phone", "textMultiLine"),
        "ellipsize" to listOf("none", "start", "middle", "end", "marquee"),
        "exported" to listOf("true", "false"),
        "allowBackup" to listOf("true", "false"),
        "enabled" to listOf("true", "false"),
        "clickable" to listOf("true", "false"),
        "focusable" to listOf("true", "false"),
        "required" to listOf("true", "false"),
        "launchMode" to listOf("standard", "singleTop", "singleTask", "singleInstance"),
        "screenOrientation" to listOf("portrait", "landscape", "sensor", "behind"),
    )


}
