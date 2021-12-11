package svechnikov.mynalabs

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import java.io.IOException
import kotlin.math.min
import kotlin.math.round

@ExperimentalUseCaseGroup
class MainActivity : AppCompatActivity() {

    private var recorder: CameraRecorder? = null

    private var recorderTouchHandler: RecorderTouchHandler? = null

    private lateinit var surfaceView: SurfaceView

    private var surface: Surface? = null

    private var state: State = State.RequestingPermissions

    private lateinit var recordButton: View

    private lateinit var closeButton: View

    private var mediaPlayer: MediaPlayer? = null

    private val previewSurfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surface = holder.surface
            checkSurfaceAndPermissions()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surface = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        closeButton = findViewById(R.id.close)
        closeButton.setOnClickListener {
            state = State.ShowingPreview
            applyState()
        }
        recordButton = findViewById(R.id.record)
        recordButton.setOnTouchListener { _, event ->
            recorderTouchHandler?.processEvent(event)
            true
        }
        surfaceView = findViewById(R.id.surface)
        surfaceView.holder.addCallback(previewSurfaceCallback)
    }

    private fun checkSurfaceAndPermissions() {
        if (allPermissionsGranted() && surface != null) {
            if (state == State.RequestingPermissions) {
                state = State.ShowingPreview
            }
            applyState()
        }
    }

    private fun applyState() {
        updateUi()
        when (state) {
            State.ShowingPreview -> startCamera()
            is State.ShowingVideo -> startVideoPlayback()
        }
    }

    override fun onStart() {
        super.onStart()

        checkSurfaceAndPermissions()
    }

    private fun updateUi() {
        when (state) {
            State.ShowingPreview -> {
                recordButton.isVisible = true
                closeButton.isVisible = false
            }
            State.Recording -> {

            }
            is State.ShowingVideo -> {
                recordButton.isVisible = false
                closeButton.isVisible = true
            }
            else -> {

            }
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
                checkSurfaceAndPermissions()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
            }
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.setSurface(null)
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        mediaPlayer = null
    }

    private fun startCamera() {
        releasePlayer()

        val surface = surface ?: return
        val recorder = CameraRecorder(
            this,
            surface,
            ::adjustPreviewSize,
            ::onVideoRecorded,
            ::onRecordingProgressUpdated,
        ).also { this.recorder = it }

        recorderTouchHandler = RecorderTouchHandler(recorder)
    }

    private fun onVideoRecorded(path: String) = runOnUiThread {
        recorder?.destroy()
        recorder = null
        state = State.ShowingVideo(path)
        applyState()
    }

    private fun startVideoPlayback() {
        val state = state as? State.ShowingVideo ?: return

        mediaPlayer = MediaPlayer().also {
            it.setDataSource(this, Uri.parse(state.path))
            it.isLooping = true
            it.setSurface(surface)
            it.setOnPreparedListener {
                it.start()
            }
            it.prepareAsync()
        }
    }

    private fun onRecordingProgressUpdated(seconds: Int) {

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

        releasePlayer()

        recorder?.destroy()
        recorder = null
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private sealed class State {
        object RequestingPermissions : State()
        object ShowingPreview : State()
        object Recording : State()
        data class ShowingVideo(val path: String) : State()
    }

    private companion object {
        const val REQUEST_CODE_PERMISSIONS = 10
        val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}