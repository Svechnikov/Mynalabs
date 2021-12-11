package svechnikov.mynalabs

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Size
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalUseCaseGroup
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.sin

@ExperimentalUseCaseGroup
class CameraRecorder(
    activity: AppCompatActivity,
    previewSurface: Surface,
    frameSizeCallback: (Size) -> Size,
    private val onVideoRecorded: (path: String) -> Unit,
    private val onRecordingProgressUpdated: (Int) -> Unit,
) {

    private val eglExecutor = Executors.newSingleThreadExecutor()

    private var destroyed = false

    private var cameraSurfaceTexture: SurfaceTexture? = null

    private val eglCore = EGLCore()

    private var previewEglSurface: EGLSurface? = null

    private val transformMatrix = FloatArray(16)

    private var videoProgram: CameraTextureProgram? = null

    private var logo: Bitmap? = null

    private var viewSize: Size? = null

    private var frameSize: Size? = null

    private var cameraTexture: ExternalTexture? = null

    private var bitmapTexture: BitmapTexture? = null

    private var bitmapProgram: BitmapTextureProgram? = null

    private var startTime = 0L

    private var encoder: Encoder? = null

    private var encoderEglSurface: EGLSurface? = null

    private val filePath = File(activity.filesDir, "video.mp4").absolutePath

    init {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()

            preview.setSurfaceProvider(eglExecutor) { request ->
                if (eglExecutor.isShutdown) {
                    request.willNotProvideSurface()
                    return@setSurfaceProvider
                }
                val logo = ContextCompat.getDrawable(
                    activity,
                    LogoConfig.LOGO_RESOURCE,
                )!!.toBitmap().also {
                    logo = it
                }

                request.setTransformationInfoListener(eglExecutor) {
                    val size = if (it.rotationDegrees == 90 || it.rotationDegrees == 270) {
                        Size(it.cropRect.height(), it.cropRect.width())
                    } else {
                        Size(it.cropRect.width(), it.cropRect.height())
                    }
                    val viewSize = frameSizeCallback(size).also { this.viewSize = it }

                    frameSize = size

                    previewEglSurface = eglCore
                        .createSurface(previewSurface)
                        .also(eglCore::makeCurrent)

                    videoProgram = CameraTextureProgram()
                    val texture = ExternalTexture().also { cameraTexture = it }

                    bitmapProgram = BitmapTextureProgram()
                    bitmapTexture = BitmapTexture(logo)

                    cameraSurfaceTexture = SurfaceTexture(texture.texId).also {
                        it.setDefaultBufferSize(viewSize.width, viewSize.height)
                        it.setOnFrameAvailableListener(::onFrameAvailable)
                    }
                    request.provideSurface(Surface(cameraSurfaceTexture), eglExecutor) {

                    }
                }
            }
            val selector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(activity, selector, preview)
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        if (eglExecutor.isShutdown) {
            return
        }
        eglExecutor.execute {
            val previewSurface = previewEglSurface ?: return@execute
            val viewSize = viewSize ?: return@execute
            val texture = cameraTexture ?: return@execute
            val bitmapTexture = bitmapTexture ?: return@execute

            if (startTime == 0L) {
                startTime = System.currentTimeMillis()
            }
            val timeSinceStart = System.currentTimeMillis() - startTime
            val halfPeriod = LogoConfig.BLINK_PERIOD / 2
            val progress = (timeSinceStart % halfPeriod) / halfPeriod
            val alpha = 1 - LogoConfig.BLINK_INTENSITY * sin(progress * Math.PI).toFloat()

            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(transformMatrix)

            eglCore.makeCurrent(previewSurface)
            GLES20.glViewport(0, 0, viewSize.width, viewSize.height)

            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            videoProgram?.draw(texture.texId, transformMatrix)
            bitmapProgram?.draw(bitmapTexture.texId, alpha)
            GLES20.glDisable(GLES20.GL_BLEND)
            eglCore.swapBuffers(previewSurface)

            encoderEglSurface?.let {
                val frameSize = frameSize ?: return@let
                val encoder = encoder ?: return@let

                eglCore.makeCurrent(it)
                GLES20.glViewport(0, 0, frameSize.width, frameSize.height)
                videoProgram?.draw(texture.texId, transformMatrix)
                encoder.process()
                eglCore.setPresentationTime(it, surfaceTexture.timestamp)
                eglCore.swapBuffers(it)
            }
        }
    }

    fun start() {
        val frameSize = frameSize ?: return

        Encoder(filePath, frameSize.width, frameSize.height).also {
            encoderEglSurface = eglCore.createSurface(it.surface)

            encoder = it
        }
    }

    fun stop() {
        eglExecutor.execute {
            encoder?.shutdown()
            encoder = null
            onVideoRecorded(filePath)
        }
    }

    fun destroy() {
        destroyed = true
        eglExecutor.execute {
            previewEglSurface?.let(eglCore::releaseSurface)
            eglCore.release()
        }
        eglExecutor.shutdown()
        eglExecutor.awaitTermination(2, TimeUnit.SECONDS)
        previewEglSurface = null
    }
}