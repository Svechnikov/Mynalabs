package svechnikov.mynalabs

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

class Vibrator(private val context: Context) {

    fun vibrate() {
        val vibrator = (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    VIBRATION_DURAION,
                    VibrationEffect.DEFAULT_AMPLITUDE,
                )
            )
        } else {
            vibrator.vibrate(VIBRATION_DURAION)
        }
    }


    private companion object {
        const val VIBRATION_DURAION = 20L
    }
}