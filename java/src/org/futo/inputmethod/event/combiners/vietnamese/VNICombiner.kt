package org.futo.inputmethod.event.combiners.vietnamese

import org.futo.inputmethod.event.Combiner
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.engine.general.VietnameseIMESettings
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.DataStoreHelper
import java.util.ArrayList

class VNICombiner : Combiner {
    private var handle: Long = 0
    private var feedback: String = ""

    init {
        handle = BambooEngine.nativeNew(1) // 1 = VNI
    }

    override fun processEvent(
        previousEvents: ArrayList<Event?>?,
        event: Event?
    ): Event {
        if (event == null) return Event.createNotHandledEvent()
        if (event.eventType != Event.EVENT_TYPE_INPUT_KEYPRESS) return event

        val keypress = event.mCodePoint.toChar()

        // The normal ASCII digits are left untouched by the combiner and always result in digits
        // being committed to the output. On the other hand, fullwidth digits are intercepted by
        // this combiner, converted into ASCII digits, and sent to the VNI converter.
        // This lets the user explicitly enter numbers that will not get converted into diacritics.
        // For example, if ASCII '1' (U+0031 DIGIT ONE) is given to this combiner, it will always
        // output an ASCII '1' (U+0031).
        // But if a fullwidth '１' (U+FF11 FULLWIDTH DIGIT ONE) is given to this combiner, it will be
        // converted to an ASCII '1' (U+0031) and given to the VNI converter, where it might result
        // in an acute accent being placed over a letter.
        // So, the input sequence [V][i][e][t][U+FF15][U+FF16] will result in the output "Việt"
        if (keypress.code in 0xFF10..0xFF19) {
            feedback = BambooEngine.nativeProcess(handle, keypress.code - 0xFEE0)
            return Event.createConsumedEvent(event)
        }

        if (!(keypress in 'A'..'Z' || keypress in 'a'..'z')) {
            if (feedback.isNotEmpty() && event.mKeyCode == Constants.CODE_DELETE) {
                if (DataStoreHelper.getSetting(VietnameseIMESettings.DeleteWholeCharOnBackspace)) {
                    BambooEngine.nativeRemoveLastOutputChar(handle)
                    feedback = BambooEngine.nativeOutput(handle)
                } else {
                    BambooEngine.nativeRemoveLastChar(handle)
                    feedback = BambooEngine.nativeOutput(handle)
                }
                return Event.createConsumedEvent(event)
            }

            if (!event.isFunctionalKeyEvent) return Event.createResetEvent(event)
            return event
        }

        feedback = BambooEngine.nativeProcess(handle, event.mCodePoint)
        return Event.createConsumedEvent(event)
    }

    override fun getCombiningStateFeedback(): CharSequence? = feedback

    override fun reset() {
        BambooEngine.nativeReset(handle)
        feedback = ""
    }

    @Deprecated("Deprecated in Java")
    protected fun finalize() {
        if (handle != 0L) {
            BambooEngine.nativeFree(handle)
            handle = 0L
        }
    }
}
