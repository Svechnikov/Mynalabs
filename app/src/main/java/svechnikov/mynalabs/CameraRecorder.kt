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
    private val onTooShortVideo: () -> Unit,
) {

    private val eglExecutor = Executors.newSingleThreadExecutor()

    private var destroyed = false

    private var cameraSurfaceTexture: SurfaceTexture? = null

    private val eglCore = EGLCore()

    private var previewEglSurface: EGLSurface? = null

    private val transformMatrix = FloatArray(16)

    private var cameraProgram: CameraTextureProgram? = null

    private var watermark: Bitmap? = null

    private var viewSize: Size? = null

    private var frameSize: Size? = null

    private var cameraTexture: CameraTexture? = null

    private var watermarkTexture: WatermarkTexture? = null

    private var watermarkProgram: WatermarkTextureProgram? = null

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
                    WatermarkConfig.RESOURCE,
                )!!.toBitmap().also {
                    watermark = it
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

                    cameraProgram = CameraTextureProgram()
                    val texture = CameraTexture().also { cameraTexture = it }

                    watermarkProgram = WatermarkTextureProgram()
                    watermarkTexture = WatermarkTexture(logo)

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
            val bitmapTexture = watermarkTexture ?: return@execute

            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(transformMatrix)

            eglCore.makeCurrent(previewSurface)
            GLES20.glViewport(0, 0, viewSize.width, viewSize.height)

            cameraProgram?.draw(texture.texId, transformMatrix)
            eglCore.swapBuffers(previewSurface)

            encoderEglSurface?.let {
                val frameSize = frameSize ?: return@let
                val encoder = encoder ?: return@let

                val timeSinceStart = System.currentTimeMillis() - startTime
                val period = 1 / WatermarkConfig.PULSE_FREQUENCY * 1000
                val halfPeriod = period / 2
                val progress = (timeSinceStart % halfPeriod) / halfPeriod
                val alpha = WatermarkConfig.PULSE_AMPLITUDE * sin(progress * Math.PI).toFloat()

                eglCore.makeCurrent(it)
                GLES20.glViewport(0, 0, frameSize.width, frameSize.height)
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                cameraProgram?.draw(texture.texId, transformMatrix)
                watermarkProgram?.draw(bitmapTexture.texId, alpha)
                GLES20.glDisable(GLES20.GL_BLEND)
                encoder.process()
                eglCore.setPresentationTime(it, surfaceTexture.timestamp)
                eglCore.swapBuffers(it)
            }
        }
    }

    fun start() {
        val frameSize = frameSize ?: return

        startTime = System.currentTimeMillis()
        Encoder(filePath, frameSize.width, frameSize.height).also {
            encoderEglSurface = eglCore.createSurface(it.surface)

            encoder = it
        }
    }

    fun stop() {
        eglExecutor.execute {
            encoder?.shutdown()
            encoder = null
            val duration = System.currentTimeMillis() - startTime
            if (duration > 2000) {
                onVideoRecorded(filePath)
            } else {
                encoderEglSurface = null
                onTooShortVideo()
            }
            startTime = 0L
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