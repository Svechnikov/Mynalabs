package svechnikov.mynalabs

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils

class BitmapTexture(bitmap: Bitmap) {

    val texId: Int

    init {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        Utils.checkGlError("glGenTextures")

        texId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        Utils.checkGlError("glBindTexture $texId")

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        Utils.checkGlError("glTexParameter")
    }
}