package svechnikov.mynalabs

import android.view.MotionEvent

class RecorderTouchHandler(private val recorder: CameraRecorder) {
    fun processEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> recorder.start()
            MotionEvent.ACTION_UP -> recorder.stop()
        }
    }
}