package com.ahmadkharfan.androidstudiolite.feature.terminal

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat

/** Callbacks from [TerminalInputEditText] — text and special keys forwarded to the PTY. */
interface TerminalInputListener {
    fun onText(text: String)
    fun onSpecialKey(key: TerminalKey)
}

/**
 * Invisible text editor that owns the IME for the terminal. Touch gestures (scroll, long-press copy)
 * are handled by a Compose overlay in [TerminalEmulatorView]; this view only receives focus for
 * keyboard input.
 */
class TerminalInputEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatEditText(context, attrs) {

    var listener: TerminalInputListener? = null
    /** Backup path for volume keys when the focused view receives them before the Activity. */
    var volumeKeyHandler: ((volumeUp: Boolean) -> Boolean)? = null

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setTextColor(android.graphics.Color.TRANSPARENT)
        setHintTextColor(android.graphics.Color.TRANSPARENT)
        isCursorVisible = false
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = false
        isLongClickable = false
        setTextIsSelectable(false)
        customSelectionActionModeCallback = DisabledActionMode
        // When focus moves elsewhere (tapping the editor, another view), drop the soft keyboard.
        onFocusChangeListener = android.view.View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) hideKeyboard()
        }
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val added = s?.toString().orEmpty()
                if (added.isEmpty()) return
                listener?.onText(added)
                if (length() > 0) setText("")
            }
        })
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return TerminalInputConnection(base)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> return volumeKeyHandler?.invoke(false) == true
                KeyEvent.KEYCODE_VOLUME_UP -> return volumeKeyHandler?.invoke(true) == true
            }
            val special = hardwareKey(keyCode)
            if (special != null) {
                listener?.onSpecialKey(special)
                return true
            }
            if (event.unicodeChar != 0 && !event.isCtrlPressed && !event.isAltPressed) {
                val ch = event.unicodeChar.toChar()
                if (!ch.isISOControl()) {
                    listener?.onText(ch.toString())
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> return volumeKeyHandler?.invoke(false) == true
                KeyEvent.KEYCODE_VOLUME_UP -> return volumeKeyHandler?.invoke(true) == true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Request focus and show the soft keyboard. */
    fun showKeyboard() {
        requestFocus()
        post {
            val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java)
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** Hide the soft keyboard (used when entering text-selection mode). */
    fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    private inner class TerminalInputConnection(
        target: InputConnection,
    ) : InputConnectionWrapper(target, true) {

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val chunk = text?.toString().orEmpty()
            if (chunk.isNotEmpty()) listener?.onText(chunk)
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            repeat(beforeLength.coerceAtLeast(0)) { listener?.onSpecialKey(TerminalKey.Backspace) }
            repeat(afterLength.coerceAtLeast(0)) { listener?.onSpecialKey(TerminalKey.Delete) }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val special = hardwareKey(event.keyCode)
                if (special != null) {
                    listener?.onSpecialKey(special)
                    return true
                }
            }
            return super.sendKeyEvent(event)
        }
    }

    private companion object {
        val DisabledActionMode = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
            override fun onDestroyActionMode(mode: ActionMode?) = Unit
        }

        fun hardwareKey(keyCode: Int): TerminalKey? = when (keyCode) {
            KeyEvent.KEYCODE_DEL -> TerminalKey.Backspace
            KeyEvent.KEYCODE_FORWARD_DEL -> TerminalKey.Delete
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> TerminalKey.Enter
            KeyEvent.KEYCODE_TAB -> TerminalKey.Tab
            KeyEvent.KEYCODE_ESCAPE -> TerminalKey.Escape
            KeyEvent.KEYCODE_DPAD_UP -> TerminalKey.ArrowUp
            KeyEvent.KEYCODE_DPAD_DOWN -> TerminalKey.ArrowDown
            KeyEvent.KEYCODE_DPAD_LEFT -> TerminalKey.ArrowLeft
            KeyEvent.KEYCODE_DPAD_RIGHT -> TerminalKey.ArrowRight
            else -> null
        }
    }
}
