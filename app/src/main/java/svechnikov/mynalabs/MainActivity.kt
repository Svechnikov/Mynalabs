package svechnikov.mynalabs

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private var recorder: Recorder? = null

    private var recorderTouchHandler: RecorderTouchHandler? = null

    private lateinit var button: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.record).setOnTouchListener { _, event ->
            recorderTouchHandler?.processEvent(event)
            true
        }
    }

    override fun onStart() {
        super.onStart()

        if (allPermissionsGranted()) {
            allowRecording()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun allowRecording() {
        val recorder = Recorder().also { this.recorder = it }

        recorderTouchHandler = RecorderTouchHandler(recorder)
    }

    override fun onStop() {
        super.onStop()

        recorder?.destroy()
        recorder = null
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val REQUEST_CODE_PERMISSIONS = 10
        val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}