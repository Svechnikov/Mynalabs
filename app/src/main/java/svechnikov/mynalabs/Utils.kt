package svechnikov.mynalabs

import android.opengl.GLES20

object Utils {

    const val SIZE_OF_FLOAT = 4

    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            throw RuntimeException("$op: glError 0x${Integer.toHexString(error)}")
        }
    }
}