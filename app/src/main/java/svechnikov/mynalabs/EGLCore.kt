package svechnikov.mynalabs

import android.opengl.*

class EGLCore {

    private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)

    private val context: EGLContext

    private val config: EGLConfig

    init {
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw RuntimeException()
        }
        val sharedContext = EGL14.EGL_NO_CONTEXT

        config = chooseConfig()
        context = EGL14.eglCreateContext(
            display,
            config,
            sharedContext,
            intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE,
            ),
            0,
        )
        checkError()
    }

    fun createSurface(surface: Any): EGLSurface = EGL14.eglCreateWindowSurface(
        display,
        config,
        surface,
        intArrayOf(EGL14.EGL_NONE),
        0,
    ).also { checkError() }

    fun makeCurrent(surface: EGLSurface) {
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            throw RuntimeException()
        }
    }

    fun setPresentationTime(surface: EGLSurface, nsecs: Long) =
        EGLExt.eglPresentationTimeANDROID(display, surface, nsecs)

    fun swapBuffers(surface: EGLSurface) {
        EGL14.eglSwapBuffers(display, surface)
    }

    private fun checkError() {
        if (EGL14.eglGetError() != EGL14.EGL_SUCCESS) {
            throw RuntimeException()
        }
    }

    private fun chooseConfig(): EGLConfig {
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = intArrayOf(0)
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val result = EGL14.eglChooseConfig(
            display,
            attribs,
            0,
            configs,
            0,
            configs.size,
            numConfigs,
            0,
        )

        if (!result) {
            throw RuntimeException("EGLConfig not found")
        }

        return configs[0] ?: throw RuntimeException()
    }

    private companion object {
        const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}