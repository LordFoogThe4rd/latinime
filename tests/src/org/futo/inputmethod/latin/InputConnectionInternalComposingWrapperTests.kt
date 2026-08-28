/*
 * Regression tests for InputConnectionInternalComposingWrapper.
 *
 * The wrapper deliberately avoids the framework composing API (see
 * futo-org/android-keyboard#1519) and simulates composition itself with
 * commitText + deleteSurroundingText, tracking the region in composingStart /
 * composingEnd / composingText. These tests pin the lifetime of that tracked
 * region: it must be dropped as soon as it stops describing the editor, and no
 * code path may silently discard text.
 */

package org.futo.inputmethod.latin

import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min

/**
 * A minimal editor: a text buffer and a collapsed cursor. Only the methods the
 * wrapper actually reaches for are meaningful; the rest satisfy the interface.
 */
private class FakeEditor : InputConnection {
    val text = StringBuilder()
    var cursor = 0

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence =
        text.substring(max(0, cursor - n), cursor)

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence =
        text.substring(cursor, min(text.length, cursor + n))

    override fun commitText(t: CharSequence, newCursorPosition: Int): Boolean {
        text.insert(cursor, t)
        cursor += t.length
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        val start = max(0, cursor - beforeLength)
        text.delete(start, cursor)
        cursor = start
        text.delete(cursor, min(text.length, cursor + afterLength))
        return true
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        cursor = start.coerceIn(0, text.length)
        return true
    }

    // The wrapper only ever asks the target to forget its composing region; it
    // never asks it to hold one, so there is nothing to model here.
    override fun finishComposingText(): Boolean = true
    override fun setComposingRegion(start: Int, end: Int): Boolean = true
    override fun setComposingText(t: CharSequence, newCursorPosition: Int): Boolean = true

    // Returning null mirrors an editor that does not support extraction, which
    // is the case the wrapper has to cope with anyway.
    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null

    override fun getSelectedText(flags: Int): CharSequence? = null
    override fun getCursorCapsMode(reqModes: Int): Int = 0
    override fun deleteSurroundingTextInCodePoints(before: Int, after: Int): Boolean = false
    override fun commitCompletion(info: CompletionInfo?): Boolean = false
    override fun commitCorrection(info: CorrectionInfo?): Boolean = false
    override fun performEditorAction(actionCode: Int): Boolean = false
    override fun performContextMenuAction(id: Int): Boolean = false
    override fun beginBatchEdit(): Boolean = true
    override fun endBatchEdit(): Boolean = true
    override fun sendKeyEvent(event: KeyEvent?): Boolean = false
    override fun clearMetaKeyStates(states: Int): Boolean = false
    override fun reportFullscreenMode(enabled: Boolean): Boolean = false
    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = false
    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false
    override fun getHandler(): Handler? = null
    override fun closeConnection() {}
    override fun commitContent(info: InputContentInfo, flags: Int, opts: Bundle?): Boolean = false
}

@SmallTest
@RunWith(AndroidJUnit4::class)
class InputConnectionInternalComposingWrapperTests {

    private val editor = FakeEditor()

    // useSetComposingRegion = false and no buffering: the configuration whose
    // state machine these tests are about. Buffering only changes when the
    // resulting commands are flushed, not which commands are produced.
    private val wrapper = InputConnectionInternalComposingWrapper(false, false, editor)

    @Before
    fun seedCursor() {
        // The wrapper starts out not knowing where the cursor is. In the app
        // LatinIME tells it on the first onUpdateSelection; without that it
        // would bail out before reaching any of the logic under test.
        wrapper.cursorUpdated(0, 0, 0, 0)
    }

    /**
     * Apply an operation, then tell the wrapper where the editor's cursor ended
     * up, the way the framework does through LatinIME#onUpdateSelection.
     */
    private fun withCursorUpdate(op: () -> Unit) {
        val before = editor.cursor
        op()
        wrapper.cursorUpdated(before, before, editor.cursor, editor.cursor)
    }

    private fun compose(text: String) = withCursorUpdate { wrapper.setComposingText(text, 1) }
    private fun commit(text: String) = withCursorUpdate { wrapper.commitText(text, 1) }
    private fun backspace() = withCursorUpdate { wrapper.deleteSurroundingText(1, 0) }

    /**
     * The reported bug: on Vietnamese Telex, typing "ddatj" (đạt), space,
     * backspace, "j", three backspaces and then "aj" produced "đa" instead of
     * "đạ" — the tone key appeared to do nothing.
     *
     * The engine and the combiner were correct throughout and did ask for "ạ".
     * The wrapper dropped it: emptying the composition and then backspacing the
     * cursor back past composingStart left that field stale, and the rewrite of
     * "a" into "ạ" (a non-prefix change, since U+1EA1 is precomposed) fell
     * through every branch that could have applied it.
     */
    @Test
    fun toneKeyIsNotDroppedAfterBackspacingIntoThePreviousWord() {
        // "ddatj" — each step is what the Telex engine reports as the preedit.
        compose("d")
        compose("đ")
        compose("đa")
        compose("đat")
        compose("đạt")
        assertEquals("đạt", editor.text.toString())

        // Space commits the word and inserts the separator.
        commit("đạt")
        commit(" ")
        assertEquals("đạt ", editor.text.toString())

        // Backspace removes the space. Nothing is composing, so this is a plain
        // field edit.
        backspace()
        assertEquals("đạt", editor.text.toString())

        // "j" starts a new composition after the committed word.
        compose("j")
        assertEquals("đạtj", editor.text.toString())

        // Backspace pops the preedit, emptying the composition.
        compose("")
        assertEquals("đạt", editor.text.toString())

        // Two more backspaces delete committed characters, walking the cursor
        // back past where the composition used to start.
        backspace()
        backspace()
        assertEquals("đ", editor.text.toString())

        // "aj" must apply the nặng tone.
        compose("a")
        assertEquals("đa", editor.text.toString())
        compose("ạ")
        assertEquals("đạ", editor.text.toString())
    }

    /** An emptied composition leaves no region to track. */
    @Test
    fun emptyingTheCompositionClearsTheTrackedRegion() {
        compose("abc")
        assertEquals(0, wrapper.composingStart)

        compose("")
        assertEquals("", editor.text.toString())
        assertEquals(-1, wrapper.composingStart)
        assertEquals(-1, wrapper.composingEnd)
    }

    /**
     * deleteSurroundingText edits behind the composing machinery's back, so
     * whatever region was being tracked no longer describes the editor.
     */
    @Test
    fun deletingSurroundingTextClearsTheTrackedRegion() {
        commit("hello ")
        compose("abc")
        assertEquals(6, wrapper.composingStart)

        backspace()
        assertEquals("hello ab", editor.text.toString())
        assertEquals(-1, wrapper.composingStart)
        assertEquals("", wrapper.composingText)
    }

    /**
     * A rewrite that cannot reach its tracked region — the cursor sits before
     * it — must still reach the editor. Previously this branch had no body and
     * threw the text away.
     */
    @Test
    fun rewriteIsNotDroppedWhenTheCursorSitsBeforeTheTrackedRegion() {
        commit("đ")

        // Force the stale state the bug produced: a region recorded ahead of
        // where the cursor actually is.
        wrapper.composingStart = 3
        wrapper.composingText = "a"
        compose("ạ")

        assertEquals("đạ", editor.text.toString())
        assertEquals(1, wrapper.composingStart)
    }
}
