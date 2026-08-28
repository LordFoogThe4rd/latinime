/*
 * Test harness for the native-backed Vietnamese combiners.
 *
 * Drives the combiners through the shared corpus (mirrored in
 * native/bamboo-go/bamboo_android/corpus_test.go), which is the source of
 * truth after the bamboo-core migration, and covers the JNI wiring end to
 * end.
 */

package org.futo.inputmethod.event.combiners.vietnamese

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import org.futo.inputmethod.event.Combiner
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.utils.JniUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VietnameseEngineTest {

    data class Case(val id: String, val method: Int, val input: String, val expected: String)

    // Method 0 = Telex, 1 = VNI. "expected" is the bamboo result (source of truth).
    private val telexCases: List<Case> = listOf(
        Case("T1", 0, "vietej", "việt"),
        Case("T2", 0, "VIETEJ", "VIỆT"),
        Case("T3", 0, "Vietej", "Việt"),
        Case("T4", 0, "huow", "huơ"),
        Case("T5", 0, "uow", "uơ"),
        Case("T6", 0, "uwow", "ươ"),
        Case("T7", 0, "gija", "giạ"),
        Case("T8", 0, "oeo", "oeo"),
        Case("T9", 0, "oaw", "oă"),
        Case("T10", 0, "aa", "â"),
        Case("T11", 0, "ee", "ê"),
        Case("T12", 0, "oo", "ô"),
        Case("T13", 0, "dd", "đ"),
        Case("T14", 0, "ddi", "đi"),
        Case("T15", 0, "dddi", "ddi"),
        Case("T16", 0, "hoas", "hóa"),
        Case("T17", 0, "hoes", "hóe"),
        Case("T18", 0, "hoos", "hố"),
        Case("T19", 0, "huys", "húy"),
        Case("T20", 0, "huos", "húo"),
        Case("T21", 0, "gies", "gié"),
        Case("T22", 0, "aso", "áo"),
        Case("T23", 0, "sao", "sao"),
        Case("T24", 0, "quanf", "quàn"),
        Case("T25", 0, "tieengs", "tiếng"),
    )

    private val vniCases: List<Case> = listOf(
        Case("V1", 1, "viet56", "việt"),
        Case("V2", 1, "VIET56", "VIỆT"),
        Case("V3", 1, "Viet56", "Việt"),
        Case("V4", 1, "gi5a", "giạ"),
        Case("V5", 1, "a6", "â"),
        Case("V6", 1, "a8", "ă"),
        Case("V7", 1, "d9", "đ"),
        Case("V8", 1, "e6", "ê"),
        Case("V9", 1, "o6", "ô"),
        Case("V10", 1, "o7", "ơ"),
        Case("V11", 1, "u7", "ư"),
        Case("V12", 1, "uu7", "ưu"),
        Case("V13", 1, "uou7", "ươu"),
        Case("V14", 1, "hoa1", "hóa"),
        Case("V15", 1, "hoa2", "hòa"),
        Case("V16", 1, "hoa3", "hỏa"),
        Case("V17", 1, "hoa4", "hõa"),
        Case("V18", 1, "hoa5", "họa"),
        Case("V19", 1, "hoe1", "hóe"),
        Case("V20", 1, "qua1", "quá"),
        Case("V21", 1, "uong7", "ương"),
    )

    companion object {
        @JvmStatic
        @BeforeClass
        fun loadNativeLibrary() {
            JniUtils.loadNativeLibrary()
        }
    }

    private fun keyEvent(cp: Int, keyCode: Int) =
        Event.createSoftwareKeypressEvent(cp, keyCode, 0, 0, false)

    private fun letter(cp: Int) = keyEvent(cp, cp)

    private fun delete() = keyEvent(Event.NOT_A_CODE_POINT, Constants.CODE_DELETE)

    private fun type(combiner: Combiner, input: String, fullwidthDigits: Boolean) {
        for (ch in input) {
            val cp = if (fullwidthDigits && ch in '0'..'9') ch.code + 0xFEE0 else ch.code
            val result = combiner.processEvent(null, letter(cp))
            assertTrue("${input[0]}: event for U+${cp.toString(16)} should be consumed",
                result.isConsumed())
        }
    }

    // ------------------------------------------------------------------
    // bamboo side: native combiners produce the source-of-truth outputs
    // ------------------------------------------------------------------

    @Test
    fun telexCombinerMatchesBambooCorpus() {
        for (c in telexCases) {
            val combiner = VietTelexCombiner()
            type(combiner, c.input, fullwidthDigits = false)
            assertEquals("${c.id} ${c.input}", c.expected,
                combiner.getCombiningStateFeedback()?.toString())
        }
    }

    @Test
    fun vniCombinerMatchesBambooCorpus() {
        for (c in vniCases) {
            val combiner = VNICombiner()
            // ASCII digits are passthrough at the combiner level; the corpus
            // digit modifiers are fed as fullwidth digits (combiner converts
            // them back to ASCII for the engine).
            type(combiner, c.input, fullwidthDigits = true)
            assertEquals("${c.id} ${c.input}", c.expected,
                combiner.getCombiningStateFeedback()?.toString())
        }
    }

    // ------------------------------------------------------------------
    // futo combiner-level quirks
    // ------------------------------------------------------------------

    @Test
    fun vniFullwidthDigitSequenceProducesViet() {
        // C1: [V][i][e][t][U+FF15][U+FF16] -> "Việt", all events consumed.
        val combiner = VNICombiner()
        type(combiner, "Viet\uFF15\uFF16", fullwidthDigits = true)
        assertEquals("Việt", combiner.getCombiningStateFeedback()?.toString())
    }

    @Test
    fun vniAsciiDigitPassesThroughUnconsumed() {
        // C2: ASCII digits always pass through; the engine never sees them.
        val combiner = VNICombiner()
        type(combiner, "viet", fullwidthDigits = false)
        assertEquals("viet", combiner.getCombiningStateFeedback()?.toString())

        val digit = combiner.processEvent(null, letter('1'.code))
        assertEquals(Event.EVENT_TYPE_STOP_COMPOSING, digit.eventType)
        assertEquals("viet", combiner.getCombiningStateFeedback()?.toString())
    }

    /**
     * NOT ISOLATED FROM DEVICE STATE — this test fails if the
     * "delete whole character on backspace" toggle is enabled on the device.
     *
     * VietTelexCombiner.processEvent picks between nativeRemoveLastChar and
     * nativeRemoveLastOutputChar by reading
     * VietnameseIMESettings.DeleteWholeCharOnBackspace through DataStoreHelper
     * at keypress time. This test never sets it, so it reads whatever the
     * installed app has stored and asserts the default (false) behaviour of
     * stepping back one keystroke. With the toggle on it deletes the whole
     * output character instead and the first assertion below fails with
     * expected:<viêt> but was:<việ>.
     *
     * Fixing this properly means injecting the setting into the combiner rather
     * than reading it from the global DataStore, so the test can pin it.
     */
    @Test
    fun telexBackspaceStepsComposition() {
        // C3: vietej -> việt; DEL -> viêt; DEL -> viet.
        val combiner = VietTelexCombiner()
        type(combiner, "vietej", fullwidthDigits = false)
        assertEquals("việt", combiner.getCombiningStateFeedback()?.toString())

        var result = combiner.processEvent(null, delete())
        assertTrue(result.isConsumed())
        assertEquals("viêt", combiner.getCombiningStateFeedback()?.toString())

        result = combiner.processEvent(null, delete())
        assertTrue(result.isConsumed())
        assertEquals("viet", combiner.getCombiningStateFeedback()?.toString())
    }

    @Test
    fun spaceAndPunctuationReturnResetEvent() {
        // C4: space / punctuation -> reset event (chain commits the word),
        // feedback preserved for the commit.
        val combiner = VietTelexCombiner()
        type(combiner, "vietej", fullwidthDigits = false)
        assertEquals("việt", combiner.getCombiningStateFeedback()?.toString())

        val space = combiner.processEvent(null, keyEvent(Constants.CODE_SPACE, Constants.CODE_SPACE))
        assertEquals(Event.EVENT_TYPE_STOP_COMPOSING, space.eventType)
        assertEquals("việt", combiner.getCombiningStateFeedback()?.toString())

        combiner.reset()
        type(combiner, "vietej", fullwidthDigits = false)
        val period = combiner.processEvent(null, keyEvent('.'.code, Constants.CODE_PERIOD))
        assertEquals(Event.EVENT_TYPE_STOP_COMPOSING, period.eventType)
        assertEquals("việt", combiner.getCombiningStateFeedback()?.toString())
    }

    @Test
    fun backspaceToEmptyThenRetype() {
        // C5: DEL back through every snapshot to empty, then a fresh
        // composition works.
        val combiner = VietTelexCombiner()
        type(combiner, "vietej", fullwidthDigits = false)
        assertEquals("việt", combiner.getCombiningStateFeedback()?.toString())

        for (i in 0 until 6) {
            combiner.processEvent(null, delete())
        }
        assertEquals("", combiner.getCombiningStateFeedback()?.toString())

        type(combiner, "xin", fullwidthDigits = false)
        assertEquals("xin", combiner.getCombiningStateFeedback()?.toString())
    }
}
