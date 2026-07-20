package com.ahmadkharfan.androidstudiolite.core.selection

import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View

class SelectionActionModeController(
    private val host: View,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun contentRect(outRect: Rect)
        fun canCut(): Boolean = false
        fun canCopy(): Boolean
        fun canPaste(): Boolean
        fun canSelectAll(): Boolean = true
        fun onCut() {}
        fun onCopy()
        fun onPaste()
        fun onSelectAll()
        fun onDestroyed() {}
    }

    private var actionMode: ActionMode? = null

    val isActive: Boolean get() = actionMode != null

    fun sync(show: Boolean) {
        if (!show) {
            finish()
            return
        }
        val existing = actionMode
        if (existing != null) {
            existing.invalidate()
            existing.invalidateContentRect()
            return
        }
        actionMode = host.startActionMode(ModeCallback(), ActionMode.TYPE_FLOATING)
    }

    fun finish() {
        val mode = actionMode ?: return
        actionMode = null
        mode.finish()
    }

    private inner class ModeCallback : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(Menu.NONE, android.R.id.cut, 0, android.R.string.cut)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(Menu.NONE, android.R.id.copy, 1, android.R.string.copy)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(Menu.NONE, android.R.id.paste, 2, android.R.string.paste)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(Menu.NONE, android.R.id.selectAll, 3, android.R.string.selectAll)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.findItem(android.R.id.cut)?.isVisible = callbacks.canCut()
            menu.findItem(android.R.id.copy)?.isVisible = callbacks.canCopy()
            menu.findItem(android.R.id.paste)?.isVisible = callbacks.canPaste()
            menu.findItem(android.R.id.selectAll)?.isVisible = callbacks.canSelectAll()
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                android.R.id.cut -> {
                    callbacks.onCut()
                    return true
                }
                android.R.id.copy -> {
                    callbacks.onCopy()
                    return true
                }
                android.R.id.paste -> {
                    callbacks.onPaste()
                    return true
                }
                android.R.id.selectAll -> {
                    callbacks.onSelectAll()
                    return true
                }
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            if (actionMode === mode) actionMode = null
            callbacks.onDestroyed()
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            callbacks.contentRect(outRect)
            if (outRect.isEmpty) {
                outRect.set(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1).coerceAtMost(48))
            }
        }
    }
}
