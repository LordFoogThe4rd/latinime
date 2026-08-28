package org.futo.inputmethod.event.combiners.vietnamese

import org.futo.inputmethod.event.Combiner
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.engine.general.VietnameseIMESettings
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.DataStoreHelper
import java.util.ArrayList

class VietTelexCombiner : Combiner {
    private var handle: Long = 0
    private var feedback: String = ""

    init {
        handle = BambooEngine.nativeNew(0) // 0 = Telex
    }

    override fun processEvent(
        previousEvents: ArrayList<Event?>?,
        event: Event?
    ): Event {
        if (event == null) return Event.createNotHandledEvent()
        if (event.eventType != Event.EVENT_TYPE_INPUT_KEYPRESS) return event

        val keypress = event.mCodePoint.toChar()

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
