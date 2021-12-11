package svechnikov.mynalabs

import android.view.MotionEvent
import androidx.camera.core.ExperimentalUseCaseGroup

@ExperimentalUseCaseGroup
class RecorderTouchHandler(
    private val recorder: CameraRecorder,
    private val vibrator: Vibrator,
) {
    fun processEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                vibrator.vibrate()
                recorder.start()
            }
            MotionEvent.ACTION_UP -> recorder.stop()
        }
    }
}