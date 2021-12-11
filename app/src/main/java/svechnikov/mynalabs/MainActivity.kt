package svechnikov.mynalabs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalUseCaseGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import kotlin.math.min
import kotlin.math.round

@ExperimentalUseCaseGroup
class MainActivity : AppCompatActivity() {

    private var recorder: CameraRecorder? = null

    private var recorderTouchHandler: RecorderTouchHandler? = null

    private lateinit var surfaceView: SurfaceView

    private var previewSurface: Surface? = null

    private val previewSurfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            previewSurface = holder.surface
            startCameraIfPossible()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.record).setOnTouchListener { _, event ->
            recorderTouchHandler?.processEvent(event)
            true
        }
        surfaceView = findViewById(R.id.surface)
        surfaceView.holder.addCallback(previewSurfaceCallback)
    }

    override fun onStart() {
        super.onStart()

        if (allPermissionsGranted()) {
            startCameraIfPossible()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCameraIfPossible()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
            }
        }
    }

    private fun startCameraIfPossible() {
        if (!allPermissionsGranted()) {
            return
        }

        val surface = previewSurface ?: return
        val recorder = CameraRecorder(
            this,
            surface,
            ::adjustPreviewSize,
            ::onVideoRecorded,
        ).also { this.recorder = it }

        recorderTouchHandler = RecorderTouchHandler(recorder)
    }

    private fun onVideoRecorded(path: String) {
        recorder?.destroy()
        recorder = null
    }

    private fun adjustPreviewSize(frameSize: Size): Size {
        val oldWidth = surfaceView.measuredWidth.toFloat()
        val oldHeight = surfaceView.measuredHeight.toFloat()

        val frameWidth = frameSize.width
        val frameHeight = frameSize.height

        val ratio = if (frameWidth > oldWidth || frameHeight > oldHeight) {
            min(oldWidth / frameWidth, oldHeight / frameHeight)
        } else {
            min(oldWidth / frameWidth, oldHeight / frameHeight)
        }

        val newWidth = (round(frameWidth * ratio)).toInt()
        val newHeight = (round(frameHeight * ratio)).toInt()

        runOnUiThread {
            surfaceView.updateLayoutParams<FrameLayout.LayoutParams> {
                width = newWidth
                height = newHeight
            }
            surfaceView.requestLayout()
        }

        return Size(newWidth, newHeight)
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