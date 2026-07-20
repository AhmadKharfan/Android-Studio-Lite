package com.ahmadkharfan.androidstudiolite.feature.editor.engine
data class EditRecord(
    val start: Int,
    val removed: String,
    val inserted: String,
    val caretBefore: Selection,
    val caretAfter: Selection,
)
class EditHistory(private val coalesceLimit: Int = 80) {
    private val undoStack = ArrayList<EditRecord>()
    private val redoStack = ArrayList<EditRecord>()
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    fun record(edit: EditRecord, coalesce: Boolean) {
        redoStack.clear()
        val top = undoStack.lastOrNull()
        if (coalesce && top != null && canCoalesce(top, edit)) {
            undoStack[undoStack.lastIndex] = merge(top, edit)
        } else {
            undoStack.add(edit)
        }
    }
    fun undo(): EditRecord? {
        if (undoStack.isEmpty()) return null
        val record = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(record)
        return record
    }
    fun redo(): EditRecord? {
        if (redoStack.isEmpty()) return null
        val record = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(record)
        return record
    }
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
    private fun canCoalesce(top: EditRecord, next: EditRecord): Boolean {
        val insertRun = top.removed.isEmpty() && next.removed.isEmpty() &&
            next.inserted.length == 1 && next.inserted[0] != '\n' &&
            top.inserted.isNotEmpty() && !top.inserted.endsWith("\n") &&
            top.inserted.length < coalesceLimit &&
            next.start == top.start + top.inserted.length
        val deleteRun = top.inserted.isEmpty() && next.inserted.isEmpty() &&
            next.removed.length == 1 &&
            top.removed.isNotEmpty() && top.removed.length < coalesceLimit &&
            next.start + next.removed.length == top.start
        return insertRun || deleteRun
    }
    private fun merge(top: EditRecord, next: EditRecord): EditRecord =
        if (top.removed.isEmpty()) {
            top.copy(inserted = top.inserted + next.inserted, caretAfter = next.caretAfter)
        } else {
            top.copy(start = next.start, removed = next.removed + top.removed, caretAfter = next.caretAfter)
        }
}
