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
import java.util.concurrent.Executors

@ExperimentalUseCaseGroup
class CameraRecorder(
    activity: AppCompatActivity,
    previewSurface: Surface,
    frameSizeCallback: (Size) -> Size,
) {

    private val eglExecutor = Executors.newSingleThreadExecutor()

    private var destroyed = false

    private var cameraSurfaceTexture: SurfaceTexture? = null

    private val eglCore = EGLCore()

    private var cameraTexture: SurfaceTexture? = null

    private var previewEglSurface: EGLSurface? = null

    private val transformMatrix = FloatArray(16)

    private var textureProgram: TextureProgram? = null

    private var logo: Bitmap? = null

    private var viewSize: Size? = null

    private var frameSize: Size? = null

    init {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()

            preview.setSurfaceProvider(eglExecutor) { request ->
                if (destroyed) {
                    request.willNotProvideSurface()
                    return@setSurfaceProvider
                }

                logo = ContextCompat.getDrawable(activity, R.drawable.logo)!!.toBitmap()

                request.setTransformationInfoListener(eglExecutor) {
                    val size = if (it.rotationDegrees == 90 || it.rotationDegrees == 270) {
                        Size(it.cropRect.height(), it.cropRect.width())
                    } else {
                        Size(it.cropRect.width(), it.cropRect.height())
                    }
                    val viewSize = frameSizeCallback(size).also { this.viewSize = it }

                    previewEglSurface = eglCore
                        .createSurface(previewSurface)
                        .also(eglCore::makeCurrent)

                    val textureProgram = TextureProgram().also { this.textureProgram = it }
                    cameraSurfaceTexture = SurfaceTexture(textureProgram.texId).also {
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

            cameraProvider.bindToLifecycle(activity, selector, preview)
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        if (destroyed) {
            return
        }
        eglExecutor.execute {
            val previewSurface = previewEglSurface ?: return@execute
            val viewSize = viewSize ?: return@execute

            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(transformMatrix)

            eglCore.makeCurrent(previewSurface)
            GLES20.glViewport(0, 0, viewSize.width, viewSize.height)
            textureProgram?.draw(transformMatrix)

            eglCore.swapBuffers(previewSurface)
        }
    }

    fun start() {
        println("recorder start")
    }

    fun stop() {
        println("recorder stop")
    }

    fun destroy() {
        println("recorder destroy")
        destroyed = true
        previewEglSurface = null
        eglExecutor.shutdown()
    }
}